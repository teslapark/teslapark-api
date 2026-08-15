package com.teslapark.persistence

import com.teslapark.MySqlSupport
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.event.GateEventType
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.model.Spot
import com.teslapark.domain.model.WebhookEventRecord
import com.teslapark.infrastructure.persistence.repository.JdbcOperations
import com.teslapark.infrastructure.persistence.repository.MySqlAnomalyRepository
import com.teslapark.infrastructure.persistence.repository.MySqlParkingSessionRepository
import com.teslapark.infrastructure.persistence.repository.MySqlRevenueRepository
import com.teslapark.infrastructure.persistence.repository.MySqlSectorRepository
import com.teslapark.infrastructure.persistence.repository.MySqlSpotRepository
import com.teslapark.infrastructure.persistence.repository.MySqlWebhookEventRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceAdapterTest {
    private lateinit var jdbc: JdbcOperations
    private lateinit var sectors: MySqlSectorRepository
    private lateinit var spots: MySqlSpotRepository
    private lateinit var sessions: MySqlParkingSessionRepository
    private lateinit var revenue: MySqlRevenueRepository
    private lateinit var webhookEvents: MySqlWebhookEventRepository
    private lateinit var anomalies: MySqlAnomalyRepository

    private val now = Instant.parse("2026-08-15T12:00:00Z")
    private val today = LocalDate.parse("2026-08-15")

    private val sectorA =
        Sector(
            code = SectorCode("A"),
            basePrice = Money.of("40.50"),
            maxCapacity = 10,
            openHour = LocalTime.of(0, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(1440),
        )
    private val sectorB =
        Sector(
            code = SectorCode("B"),
            basePrice = Money.of("4.10"),
            maxCapacity = 20,
            openHour = LocalTime.of(8, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(60),
        )

    private fun spotAt(
        externalId: Long,
        latitude: String,
        longitude: String,
        sector: SectorCode = sectorA.code,
    ) = Spot(externalId, sector, Coordinates.of(latitude, longitude))

    @BeforeAll
    fun setUp() {
        val jdbcUrl = MySqlSupport.createIsolatedDatabase("persistence_adapter_test")
        MySqlSupport.flywayFor(jdbcUrl).migrate()

        jdbc = JdbcOperations(MySqlSupport.dataSourceFor(jdbcUrl))
        sectors = MySqlSectorRepository(jdbc, garageName = "sp-01")
        spots = MySqlSpotRepository(jdbc)
        sessions = MySqlParkingSessionRepository(jdbc)
        revenue = MySqlRevenueRepository(jdbc)
        webhookEvents = MySqlWebhookEventRepository(jdbc)
        anomalies = MySqlAnomalyRepository(jdbc)

        sectors.synchronize(listOf(sectorA, sectorB))
    }

    @Test
    fun `sector synchronization is idempotent and money survives the round trip`() {
        sectors.synchronize(listOf(sectorA, sectorB))
        sectors.synchronize(listOf(sectorA, sectorB))

        sectors.findAll() shouldHaveSize 2
        sectors.totalCapacity() shouldBe 30

        val stored = sectors.findByCode(SectorCode("B")).shouldNotBeNull()
        stored.basePrice shouldBe Money.of("4.10")
        stored.basePrice.amount.toPlainString() shouldBe "4.10"
        stored.durationLimitMinutes shouldBe 60L
        stored.openHour shouldBe LocalTime.of(8, 0)
    }

    @Test
    fun `spot synchronization is idempotent and finds by exact coordinate`() {
        val spot = spotAt(101, "-23.561684", "-46.655981")
        spots.synchronize(listOf(spot))
        spots.synchronize(listOf(spot))

        val found = spots.findByCoordinates(Coordinates.of("-23.5616840", "-46.6559810")).shouldNotBeNull()
        found.externalId shouldBe 101L
        found.sectorCode shouldBe sectorA.code
        spots.findByCoordinates(Coordinates.of("-23.999999", "-46.999999")).shouldBeNull()
    }

    @Test
    fun `two concurrent threads lock distinct spots under skip locked`() {
        spots.synchronize(listOf(spotAt(201, "-23.561201", "-46.655201"), spotAt(202, "-23.561202", "-46.655202")))

        val locked = ConcurrentLinkedQueue<Long>()
        runConcurrently(threads = 2) {
            jdbc.inTransaction {
                spots.lockAnyFreeSpot()?.let { spot ->
                    locked += spot.externalId
                    Thread.sleep(SHORT_HOLD_MILLIS)
                }
            }
        }

        locked shouldHaveSize 2
        locked.toSet() shouldHaveSize 2
    }

    @Test
    fun `thirty concurrent requests over a single free spot allocate exactly once`() {
        val contested = spotAt(301, "-23.561301", "-46.655301")
        spots.synchronize(listOf(contested))

        val contenders =
            (1..CONTENDERS).map { index ->
                sessions.save(enteredSession(LicensePlate("CTD%04d".format(index)))).valueOrNull()!!.id!!
            }

        val allocations = AtomicInteger()
        val nextContender = AtomicInteger()
        runConcurrently(threads = CONTENDERS) {
            val sessionId = contenders[nextContender.getAndIncrement()]
            jdbc.inTransaction {
                val spot = spots.lockFreeSpotAt(contested.coordinates)
                if (spot != null && spots.occupy(spot, sessionId).valueOrNull() != null) {
                    allocations.incrementAndGet()
                }
            }
        }

        allocations.get() shouldBe 1
        spots.findByCoordinates(contested.coordinates).shouldNotBeNull().occupied shouldBe true
    }

    @Test
    fun `session round trip keeps the active plate index authoritative`() {
        val plate = LicensePlate("ZUL1001")
        val open = sessions.save(enteredSession(plate)).valueOrNull().shouldNotBeNull()
        open.id.shouldNotBeNull()

        sessions.findActiveSessionFor(plate).shouldNotBeNull().id shouldBe open.id
        sessions.save(enteredSession(plate)).errorOrNull().shouldBeInstanceOf<DomainError.SessionAlreadyOpen>()

        val closed = open.exit(now.plus(Duration.ofMinutes(130))).valueOrNull()!!
        sessions.save(closed.withCharge(Money.of("40.50"), billedHours = 3, amount = Money.of("121.50")))

        sessions.findActiveSessionFor(plate).shouldBeNull()
        val reloaded = sessions.findById(open.id!!).shouldNotBeNull()
        reloaded.amountCharged shouldBe Money.of("121.50")
        reloaded.billedHours shouldBe 3
        reloaded.occupancyRateAtEntry.toPlainString() shouldBe "0.4667"
        reloaded.priceMultiplier.toPlainString() shouldBe "1.000"
    }

    @Test
    fun `occupancy is counted by indexed query and released spots stop counting`() {
        val plate = LicensePlate("ZUL1002")
        val spot = spotAt(401, "-23.561401", "-46.655401")
        spots.synchronize(listOf(spot))

        val session = sessions.save(enteredSession(plate)).valueOrNull()!!
        val before = spots.countOccupied()

        spots.occupy(spot, session.id!!).valueOrNull().shouldNotBeNull()
        spots.countOccupied() shouldBe before + 1

        spots
            .releaseHeldBy(session.id!!)
            .valueOrNull()
            .shouldNotBeNull()
            .occupied shouldBe false
        spots.countOccupied() shouldBe before
        spots.releaseHeldBy(session.id!!).errorOrNull().shouldBeInstanceOf<DomainError.SpotNotHeld>()
    }

    @Test
    fun `revenue accumulates under concurrency without losing a cent`() {
        val entries = 50
        runConcurrently(threads = entries) {
            revenue.accumulate(RevenueEntry(sectorB.code, today, Money.of("4.10")))
        }

        val snapshot = revenue.findBy(sectorB.code, today).shouldNotBeNull()
        snapshot.total shouldBe Money.of("205.00")
        snapshot.sessionsCount shouldBe entries
        snapshot.freeSessionsCount shouldBe 0
    }

    @Test
    fun `free sessions are counted apart from the billed total`() {
        val date = LocalDate.parse("2026-08-16")

        revenue.accumulate(RevenueEntry(sectorA.code, date, Money.of("121.50")))
        revenue.accumulate(RevenueEntry(sectorA.code, date, Money.zero()))
        val snapshot = revenue.accumulate(RevenueEntry(sectorA.code, date, Money.zero()))

        snapshot.total shouldBe Money.of("121.50")
        snapshot.sessionsCount shouldBe 3
        snapshot.freeSessionsCount shouldBe 2
        revenue.findAllOn(date) shouldHaveSize 1
    }

    @Test
    fun `a rolled back exit leaves neither orphan revenue nor a stuck spot`() {
        val date = LocalDate.parse("2026-08-17")
        val plate = LicensePlate("ZUL1003")
        val spot = spotAt(501, "-23.561501", "-46.655501")
        spots.synchronize(listOf(spot))

        val session = sessions.save(enteredSession(plate)).valueOrNull()!!
        spots.occupy(spot, session.id!!)

        runCatching {
            jdbc.inTransaction {
                revenue.accumulate(RevenueEntry(sectorA.code, date, Money.of("121.50")))
                spots.releaseHeldBy(session.id!!)
                error("exit failed after the money was written")
            }
        }.isFailure shouldBe true

        revenue.findBy(sectorA.code, date).shouldBeNull()
        spots.findByCoordinates(spot.coordinates).shouldNotBeNull().occupied shouldBe true
    }

    @Test
    fun `duplicated idempotency key becomes a domain error and never a driver exception`() {
        val record =
            WebhookEventRecord(
                idempotencyKey = IdempotencyKey("b".repeat(IdempotencyKey.LENGTH)),
                eventType = GateEventType.ENTRY,
                receivedAt = now,
                rawPayload = """{"event_type":"ENTRY","license_plate":"ZUL1004"}""",
                licensePlate = LicensePlate("ZUL1004"),
                eventTime = now,
            )

        val stored = webhookEvents.registerIfAbsent(record).valueOrNull().shouldNotBeNull()
        webhookEvents
            .registerIfAbsent(record)
            .errorOrNull()
            .shouldBeInstanceOf<DomainError.DuplicateWebhookEvent>()

        webhookEvents.save(stored.markProcessed(now, sessionId = null))
        webhookEvents
            .findBy(record.idempotencyKey)
            .shouldNotBeNull()
            .rawPayload
            .contains("ZUL1004") shouldBe true
    }

    @Test
    fun `anomalies are recorded and counted by type`() {
        anomalies.record(SessionAnomaly(AnomalyType.EXIT_WITHOUT_ENTRY, now, description = "ZUL9999"))
        anomalies.record(SessionAnomaly(AnomalyType.EXIT_WITHOUT_ENTRY, now, description = "ZUL9998"))

        anomalies.countOfType(AnomalyType.EXIT_WITHOUT_ENTRY) shouldBe 2
        anomalies.countOfType(AnomalyType.DUPLICATE_ENTRY) shouldBe 0
        anomalies.findAllOfType(AnomalyType.EXIT_WITHOUT_ENTRY).first().description shouldBe "ZUL9999"
    }

    private fun enteredSession(plate: LicensePlate) =
        ParkingSession.enter(
            licensePlate = plate,
            entryTime = now,
            occupancyRateAtEntry = BigDecimal("0.4667"),
            priceMultiplier = BigDecimal("1.000"),
        )

    private fun runConcurrently(
        threads: Int,
        action: () -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) {
            pool.submit {
                start.await()
                runCatching(action)
                done.countDown()
            }
        }
        start.countDown()
        done.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pool.shutdownNow()
    }

    private companion object {
        const val CONTENDERS = 30
        const val SHORT_HOLD_MILLIS = 150L
        const val CONCURRENCY_TIMEOUT_SECONDS = 30L
    }
}
