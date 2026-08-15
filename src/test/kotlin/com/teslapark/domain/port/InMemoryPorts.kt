package com.teslapark.domain.port

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.GarageConfiguration
import com.teslapark.domain.model.GarageConfigurationStatus
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class FixedClockProvider(
    private var instant: Instant,
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
) : ClockProvider {
    override fun now(): Instant = instant

    override fun operatingZone(): ZoneId = zone

    fun advanceTo(next: Instant) {
        instant = next
    }
}

class InMemorySectorRepository : SectorRepository {
    private val sectors = linkedMapOf<SectorCode, Sector>()

    override fun findByCode(code: SectorCode): Sector? = sectors[code]

    override fun findAll(): List<Sector> = sectors.values.toList()

    override fun synchronize(sectors: List<Sector>): List<Sector> {
        sectors.forEach { this.sectors[it.code] = it }
        return findAll()
    }

    override fun totalCapacity(): Int = sectors.values.sumOf { it.maxCapacity }
}

class InMemorySpotRepository : SpotRepository {
    private val spots = linkedMapOf<Long, Spot>()
    private val holders = mutableMapOf<Long, Long>()

    override fun findByCoordinates(coordinates: Coordinates): Spot? = spots.values.firstOrNull { it.coordinates == coordinates }

    override fun lockFreeSpotAt(coordinates: Coordinates): Spot? = findByCoordinates(coordinates)?.takeUnless { it.occupied }

    override fun lockAnyFreeSpot(): Spot? = spots.values.firstOrNull { !it.occupied }

    override fun occupy(
        spot: Spot,
        sessionId: Long,
    ): DomainResult<Spot> {
        val stored =
            spots[spot.externalId]
                ?: return DomainError
                    .SpotNotFound(
                        spot.coordinates.latitude.toPlainString(),
                        spot.coordinates.longitude.toPlainString(),
                    ).asFailure()
        if (stored.occupied) return DomainError.SpotAlreadyOccupied(stored.externalId).asFailure()

        val occupied = stored.occupy()
        spots[occupied.externalId] = occupied
        holders[sessionId] = occupied.externalId
        return occupied.asSuccess()
    }

    override fun releaseHeldBy(sessionId: Long): DomainResult<Spot> {
        val externalId = holders.remove(sessionId) ?: return DomainError.SpotNotHeld(sessionId).asFailure()
        val released = spots.getValue(externalId).release()
        spots[externalId] = released
        return released.asSuccess()
    }

    override fun synchronize(spots: List<Spot>): List<Spot> {
        spots.forEach { this.spots.putIfAbsent(it.externalId, it) }
        return this.spots.values.toList()
    }

    override fun countOccupied(): Int = spots.values.count { it.occupied }
}

class InMemoryParkingSessionRepository : ParkingSessionRepository {
    private val sessions = linkedMapOf<Long, ParkingSession>()
    private val sequence = AtomicLong()

    override fun findActiveSessionFor(licensePlate: LicensePlate): ParkingSession? =
        sessions.values.firstOrNull { it.licensePlate == licensePlate && !it.isClosed }

    override fun findById(id: Long): ParkingSession? = sessions[id]

    override fun save(session: ParkingSession): DomainResult<ParkingSession> {
        if (session.id == null) {
            val alreadyOpen = findActiveSessionFor(session.licensePlate)
            if (alreadyOpen != null) return DomainError.SessionAlreadyOpen(session.licensePlate.value).asFailure()

            val persisted = session.copy(id = sequence.incrementAndGet())
            sessions[persisted.id!!] = persisted
            return persisted.asSuccess()
        }

        sessions[session.id] = session
        return session.asSuccess()
    }

    override fun countOpenSessions(): Int = sessions.values.count { !it.isClosed }

    override fun sumChargedOn(revenueDate: LocalDate): Map<SectorCode, Money> =
        sessions.values
            .filter { it.revenueDate == revenueDate && it.amountCharged != null && it.sectorCode != null }
            .groupBy { it.sectorCode!! }
            .mapValues { (_, group) -> group.map { it.amountCharged!! }.reduce(Money::plus) }
}

class InMemoryRevenueRepository : RevenueRepository {
    private val snapshots = linkedMapOf<Pair<SectorCode, LocalDate>, DailyRevenue>()

    override fun accumulate(entry: RevenueEntry): DailyRevenue {
        val key = entry.sectorCode to entry.revenueDate
        val current = snapshots[key] ?: DailyRevenue.empty(entry.sectorCode, entry.revenueDate)
        val updated = current.accumulate(entry)
        snapshots[key] = updated
        return updated
    }

    override fun findBy(
        sectorCode: SectorCode,
        revenueDate: LocalDate,
    ): DailyRevenue? = snapshots[sectorCode to revenueDate]

    override fun findAllOn(revenueDate: LocalDate): List<DailyRevenue> = snapshots.values.filter { it.revenueDate == revenueDate }
}

class InMemoryGarageConfigurationProvider(
    private var configuration: GarageConfiguration? = null,
    private var failure: DomainError? = null,
) : GarageConfigurationProvider {
    var fetchCount: Int = 0
        private set

    override fun fetchConfiguration(): DomainResult<GarageConfiguration> {
        fetchCount++
        failure?.let { return it.asFailure() }
        return configuration?.asSuccess() ?: DomainError.GarageConfigurationUnavailable.asFailure()
    }

    fun respondWith(configuration: GarageConfiguration) {
        this.configuration = configuration
        this.failure = null
    }

    fun failWith(error: DomainError) {
        this.failure = error
    }
}

class InMemoryWebhookEventRepository : WebhookEventRepository {
    private val events = linkedMapOf<IdempotencyKey, WebhookEventRecord>()
    private val sequence = AtomicLong()

    override fun registerIfAbsent(event: WebhookEventRecord): DomainResult<WebhookEventRecord> {
        events[event.idempotencyKey]?.let { return DomainError.DuplicateWebhookEvent(it.idempotencyKey.value).asFailure() }

        val persisted = event.copy(id = sequence.incrementAndGet())
        events[persisted.idempotencyKey] = persisted
        return persisted.asSuccess()
    }

    override fun findBy(idempotencyKey: IdempotencyKey): WebhookEventRecord? = events[idempotencyKey]

    override fun save(event: WebhookEventRecord): WebhookEventRecord {
        events[event.idempotencyKey] = event
        return event
    }

    override fun discard(idempotencyKey: IdempotencyKey) {
        events.remove(idempotencyKey)
    }
}

class InMemoryAnomalyRepository : AnomalyRepository {
    private val anomalies = mutableListOf<SessionAnomaly>()
    private val sequence = AtomicLong()

    override fun record(anomaly: SessionAnomaly): SessionAnomaly {
        val persisted = anomaly.copy(id = sequence.incrementAndGet())
        anomalies += persisted
        return persisted
    }

    override fun findAllOfType(type: AnomalyType): List<SessionAnomaly> = anomalies.filter { it.type == type }

    override fun countOfType(type: AnomalyType): Int = findAllOfType(type).size
}

class InMemoryGarageStateRepository : GarageStateRepository {
    private var status = GarageConfigurationStatus.PENDING
    private var syncedAt: Instant? = null
    private var capacity = 0

    override fun currentStatus(): GarageConfigurationStatus = status

    override fun lastSyncAt(): Instant? = syncedAt

    override fun totalCapacity(): Int = capacity

    override fun markSynced(
        at: Instant,
        totalCapacity: Int,
    ) {
        status = GarageConfigurationStatus.SYNCED
        syncedAt = at
        capacity = totalCapacity
    }

    override fun markStale() {
        if (status == GarageConfigurationStatus.SYNCED) status = GarageConfigurationStatus.STALE
    }
}

class InMemoryTransactionBoundary : TransactionBoundary {
    var transactions: Int = 0
        private set

    override fun <T> inTransaction(block: () -> T): T {
        transactions++
        return block()
    }
}
