package com.teslapark.domain.port

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.event.GateEventType
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.Garage
import com.teslapark.domain.model.GarageConfiguration
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Occupancy
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.model.Spot
import com.teslapark.domain.model.WebhookEventRecord
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class PortContractTest {
    private val now = Instant.parse("2026-08-15T12:00:00Z")
    private val plate = LicensePlate("ZUL0001")
    private val coordinates = Coordinates.of("-23.561684", "-46.655981")

    private val clock = FixedClockProvider(now)
    private val sectors = InMemorySectorRepository()
    private val spots = InMemorySpotRepository()
    private val sessions = InMemoryParkingSessionRepository()
    private val revenue = InMemoryRevenueRepository()
    private val webhookEvents = InMemoryWebhookEventRepository()
    private val anomalies = InMemoryAnomalyRepository()
    private val configuration = InMemoryGarageConfigurationProvider()

    private val sectorA =
        Sector(
            code = SectorCode("A"),
            basePrice = Money.of("40.50"),
            maxCapacity = 10,
            openHour = LocalTime.of(0, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(1440),
        )
    private val spotOne = Spot(externalId = 1, sectorCode = sectorA.code, coordinates = coordinates)

    private fun enteredSession() =
        ParkingSession.enter(
            licensePlate = plate,
            entryTime = now,
            occupancyRateAtEntry = BigDecimal("0.4667"),
            priceMultiplier = BigDecimal("1.000"),
        )

    @Test
    fun `clock provider exposes the operating instant and zone`() {
        clock.now() shouldBe now
        clock.operatingZone().id shouldBe "America/Sao_Paulo"
        clock.today().toString() shouldBe "2026-08-15"
        clock.localTimeOf(now) shouldBe LocalTime.of(9, 0)
    }

    @Test
    fun `sector repository synchronizes without duplicating`() {
        sectors.synchronize(listOf(sectorA))
        sectors.synchronize(listOf(sectorA))

        sectors.findAll().size shouldBe 1
        sectors.findByCode(SectorCode("A")) shouldBe sectorA
        sectors.findByCode(SectorCode("Z")).shouldBeNull()
        sectors.totalCapacity() shouldBe 10
    }

    @Test
    fun `spot repository finds by coordinate and allocates exclusively`() {
        spots.synchronize(listOf(spotOne))

        spots.findByCoordinates(coordinates).shouldNotBeNull()
        spots.lockFreeSpotAt(coordinates).shouldNotBeNull()

        spots.occupy(spotOne, sessionId = 1).valueOrNull()!!.occupied shouldBe true
        spots.countOccupied() shouldBe 1
        spots.lockFreeSpotAt(coordinates).shouldBeNull()

        spots.occupy(spotOne, sessionId = 2).errorOrNull().shouldBeInstanceOf<DomainError.SpotAlreadyOccupied>()

        spots.releaseHeldBy(sessionId = 1).valueOrNull()!!.occupied shouldBe false
        spots.countOccupied() shouldBe 0
        spots.releaseHeldBy(sessionId = 1).errorOrNull().shouldBeInstanceOf<DomainError.SpotNotHeld>()
    }

    @Test
    fun `session repository answers the active session by plate`() {
        sessions.findActiveSessionFor(plate).shouldBeNull()

        val open = sessions.save(enteredSession()).valueOrNull()!!
        open.id.shouldNotBeNull()
        sessions.findActiveSessionFor(plate) shouldBe open
        sessions.findById(open.id!!) shouldBe open
        sessions.countOpenSessions() shouldBe 1

        sessions.save(enteredSession()).errorOrNull().shouldBeInstanceOf<DomainError.SessionAlreadyOpen>()

        val closed = open.exit(now.plusSeconds(3600)).valueOrNull()!!
        sessions.save(closed)
        sessions.findActiveSessionFor(plate).shouldBeNull()
        sessions.countOpenSessions() shouldBe 0
    }

    @Test
    fun `revenue repository accumulates the daily snapshot per sector`() {
        val today = clock.today()

        revenue.accumulate(RevenueEntry(sectorA.code, today, Money.of("121.50")))
        revenue.accumulate(RevenueEntry(sectorA.code, today, Money.zero()))
        val snapshot = revenue.accumulate(RevenueEntry(sectorA.code, today, Money.of("40.50")))

        snapshot.total shouldBe Money.of("162.00")
        snapshot.sessionsCount shouldBe 3
        snapshot.freeSessionsCount shouldBe 1
        revenue.findBy(sectorA.code, today) shouldBe snapshot
        revenue.findBy(SectorCode("B"), today).shouldBeNull()
        revenue.findAllOn(today).size shouldBe 1
    }

    @Test
    fun `webhook repository enforces idempotency on the key`() {
        val record =
            WebhookEventRecord(
                idempotencyKey = IdempotencyKey("a".repeat(IdempotencyKey.LENGTH)),
                eventType = GateEventType.ENTRY,
                receivedAt = now,
                rawPayload = """{"event_type":"ENTRY"}""",
                licensePlate = plate,
            )

        val stored = webhookEvents.registerIfAbsent(record).valueOrNull()!!
        stored.status shouldBe com.teslapark.domain.model.ProcessingStatus.RECEIVED

        webhookEvents
            .registerIfAbsent(record)
            .errorOrNull()
            .shouldBeInstanceOf<DomainError.DuplicateWebhookEvent>()

        webhookEvents.save(stored.markProcessed(now, sessionId = 42))
        webhookEvents.findBy(record.idempotencyKey)!!.sessionId shouldBe 42L
    }

    @Test
    fun `anomaly repository records inconsistent events by type`() {
        anomalies.record(SessionAnomaly(AnomalyType.EXIT_WITHOUT_ENTRY, now, licensePlate = plate))
        anomalies.record(SessionAnomaly(AnomalyType.EXIT_WITHOUT_ENTRY, now, licensePlate = plate))
        anomalies.record(SessionAnomaly(AnomalyType.PARKED_UNKNOWN_SPOT, now))

        anomalies.countOfType(AnomalyType.EXIT_WITHOUT_ENTRY) shouldBe 2
        anomalies.countOfType(AnomalyType.DUPLICATE_ENTRY) shouldBe 0
        anomalies.findAllOfType(AnomalyType.PARKED_UNKNOWN_SPOT).single().id shouldBe 3L
    }

    @Test
    fun `configuration provider reports the external source failure as a domain error`() {
        configuration.fetchConfiguration().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable

        configuration.respondWith(
            GarageConfiguration(
                garage =
                    Garage(
                        name = "sp-01",
                        timezone = Garage.DEFAULT_TIMEZONE,
                        currency = CurrencyCode.BRL,
                        sectors = listOf(sectorA),
                    ),
                spots = listOf(spotOne),
            ),
        )

        val fetched = configuration.fetchConfiguration().valueOrNull()!!
        fetched.totalCapacity shouldBe 10
        fetched.spotsOf(sectorA.code).size shouldBe 1
        configuration.fetchCount shouldBe 2
    }

    @Test
    fun `the ports compose an entry decision without any framework type`() {
        sectors.synchronize(listOf(sectorA))
        spots.synchronize(listOf(spotOne))

        val occupancy = Occupancy(sessions.countOpenSessions(), sectors.totalCapacity())

        occupancy.isFull shouldBe false
        occupancy.availableSpots shouldBe 10
    }
}
