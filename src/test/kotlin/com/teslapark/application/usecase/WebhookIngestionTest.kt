package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.event.EventResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.model.SessionStatus
import com.teslapark.domain.model.Spot
import com.teslapark.domain.policy.GlobalOccupancyPolicy
import com.teslapark.domain.policy.OccupancyTier
import com.teslapark.domain.policy.TieredPricingPolicy
import com.teslapark.domain.port.FixedClockProvider
import com.teslapark.domain.port.InMemoryAnomalyRepository
import com.teslapark.domain.port.InMemoryParkingSessionRepository
import com.teslapark.domain.port.InMemoryRevenueRepository
import com.teslapark.domain.port.InMemorySectorRepository
import com.teslapark.domain.port.InMemorySpotRepository
import com.teslapark.domain.port.InMemoryTransactionBoundary
import com.teslapark.domain.port.InMemoryWebhookEventRepository
import com.teslapark.domain.port.RecordingMetricsPublisher
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class WebhookIngestionTest {
    private val entryTime = Instant.parse("2026-08-15T12:00:00Z")
    private val plate = LicensePlate("ZUL0001")
    private val coordinates = Coordinates.of("-23.561684", "-46.655981")

    private val clock = FixedClockProvider(entryTime)
    private val sessions = InMemoryParkingSessionRepository()
    private val sectors = InMemorySectorRepository()
    private val spots = InMemorySpotRepository()
    private val revenue = InMemoryRevenueRepository()
    private val anomalies = InMemoryAnomalyRepository()
    private val webhookEvents = InMemoryWebhookEventRepository()
    private val transaction = InMemoryTransactionBoundary()
    private val metrics = RecordingMetricsPublisher()

    private val sectorA =
        Sector(
            code = SectorCode("A"),
            basePrice = Money.of("40.50"),
            maxCapacity = 1,
            openHour = LocalTime.of(0, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(1440),
        )

    private val processGateEvent =
        ProcessGateEvent(
            idempotencyGuard = EventIdempotencyGuard(),
            webhookEvents = webhookEvents,
            handleEntry = HandleEntryEvent(sessions, sectors, anomalies, GlobalOccupancyPolicy(), metrics, clock),
            handleParked = HandleParkedEvent(sessions, spots, spots, anomalies, metrics, clock),
            handleExit = HandleExitEvent(sessions, sectors, spots, revenue, anomalies, TieredPricingPolicy(), metrics, clock),
            transaction = transaction,
            metrics = metrics,
            clock = clock,
        )

    init {
        sectors.synchronize(listOf(sectorA))
        spots.synchronize(listOf(Spot(1, sectorA.code, coordinates)))
    }

    private fun send(event: GateEvent) = processGateEvent.execute(event, """{"event_type":"${event.type}"}""")

    private fun entry(plate: LicensePlate = this.plate) = GateEvent.EntryEvent(plate, entryTime)

    private fun exit(
        plate: LicensePlate = this.plate,
        minutes: Long = 130,
    ) = GateEvent.ExitEvent(plate, entryTime.plus(Duration.ofMinutes(minutes)))

    @Test
    fun `the full entry parked exit flow bills the expected amount`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()
        send(GateEvent.ParkedEvent(plate, coordinates)).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val exited = send(exit()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        exited.session.status shouldBe SessionStatus.EXITED
        exited.charge!!.chargeableHours shouldBe 3
        exited.charge!!.amount shouldBe Money.of("109.35")
        revenue.findBy(sectorA.code, clock.localDateOf(exit().exitTime))!!.total shouldBe Money.of("109.35")

        metrics.webhookEvents.map { it.second } shouldBe
            listOf(EventResult.PROCESSED, EventResult.PROCESSED, EventResult.PROCESSED)
        metrics.tiers shouldBe listOf(OccupancyTier.LOW)
        metrics.revenues.single().second shouldBe Money.of("109.35")
        metrics.stays.single() shouldBe Duration.ofMinutes(130)
        metrics.lastOccupancy!!.occupiedSpots shouldBe 0
    }

    @Test
    fun `an entry followed by an exit without parking still bills`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val exited = send(exit()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        exited.session.spotExternalId.shouldBeNull()
        exited.charge!!.amount shouldBe Money.of("109.35")
    }

    @Test
    fun `an exit without entry is an anomaly that generates no revenue`() {
        val outcome = send(exit(LicensePlate("ZUL9999"))).shouldBeInstanceOf<GateEventOutcome.Ignored>()

        outcome.anomaly shouldBe AnomalyType.EXIT_WITHOUT_ENTRY
        anomalies.countOfType(AnomalyType.EXIT_WITHOUT_ENTRY) shouldBe 1
        metrics.anomalies shouldBe listOf(AnomalyType.EXIT_WITHOUT_ENTRY)
        revenue.findAllOn(clock.today()) shouldBe emptyList()
    }

    @Test
    fun `the same entry replayed is a duplicate that changes no state`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        repeat(19) { send(entry()).shouldBeInstanceOf<GateEventOutcome.Duplicate>() }

        sessions.countOpenSessions() shouldBe 1
        anomalies.countOfType(AnomalyType.DUPLICATE_ENTRY) shouldBe 0
    }

    @Test
    fun `a second entry for the same plate at another instant is a duplicate entry anomaly`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val outcome =
            send(GateEvent.EntryEvent(plate, entryTime.plusSeconds(1)))
                .shouldBeInstanceOf<GateEventOutcome.Ignored>()

        outcome.anomaly shouldBe AnomalyType.DUPLICATE_ENTRY
        sessions.countOpenSessions() shouldBe 1
    }

    @Test
    fun `a duplicated exit credits the revenue exactly once`() {
        send(entry())
        send(exit()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        repeat(9) { send(exit()).shouldBeInstanceOf<GateEventOutcome.Duplicate>() }

        val snapshot = revenue.findBy(sectorA.code, clock.localDateOf(exit().exitTime))!!
        snapshot.total shouldBe Money.of("109.35")
        snapshot.sessionsCount shouldBe 1
    }

    @Test
    fun `a full garage rejects entry and the first exit reopens it`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val rejected = send(entry(LicensePlate("ZUL0002"))).shouldBeInstanceOf<GateEventOutcome.Rejected>()
        rejected.error shouldBe DomainError.GarageFull

        send(exit()).shouldBeInstanceOf<GateEventOutcome.Accepted>()
        send(entry(LicensePlate("ZUL0002"))).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        metrics.deniedEntries shouldBe 1
    }

    @Test
    fun `a parked event on an unknown coordinate keeps the session entered`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val outcome =
            send(GateEvent.ParkedEvent(plate, Coordinates.of("-23.999999", "-46.999999")))
                .shouldBeInstanceOf<GateEventOutcome.Ignored>()

        outcome.anomaly shouldBe AnomalyType.PARKED_UNKNOWN_SPOT
        sessions.findActiveSessionFor(plate)!!.status shouldBe SessionStatus.ENTERED
        spots.countOccupied() shouldBe 0
    }

    @Test
    fun `an out of order parked after the exit never moves the state backwards`() {
        send(entry())
        send(exit()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val outcome = send(GateEvent.ParkedEvent(plate, coordinates)).shouldBeInstanceOf<GateEventOutcome.Ignored>()

        outcome.anomaly shouldBe AnomalyType.OUT_OF_ORDER_EVENT
        sessions.findActiveSessionFor(plate).shouldBeNull()
    }

    @Test
    fun `an exit before the entry is rejected without generating revenue`() {
        send(entry())

        val rejected =
            send(GateEvent.ExitEvent(plate, entryTime.minusSeconds(60)))
                .shouldBeInstanceOf<GateEventOutcome.Rejected>()

        rejected.error.shouldBeInstanceOf<DomainError.ExitTimeBeforeEntryTime>()
        revenue.findAllOn(clock.today()) shouldBe emptyList()
    }

    @Test
    fun `the raw event is persisted before processing and marked afterwards`() {
        send(entry()).shouldBeInstanceOf<GateEventOutcome.Accepted>()

        val key = EventIdempotencyGuard().keyFor(entry())
        val stored = webhookEvents.findBy(key)!!

        stored.rawPayload shouldBe """{"event_type":"ENTRY"}"""
        stored.status.name shouldBe "PROCESSED"
        stored.sessionId shouldBe 1L
        transaction.transactions shouldBe 1
    }
}
