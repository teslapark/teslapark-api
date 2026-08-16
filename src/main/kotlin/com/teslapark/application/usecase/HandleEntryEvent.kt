package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Occupancy
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.policy.OccupancyPolicy
import com.teslapark.domain.port.AnomalyRepository
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.MetricsPublisher
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.domain.port.SectorRepository
import jakarta.inject.Singleton

@Singleton
class HandleEntryEvent(
    private val sessions: ParkingSessionRepository,
    private val sectors: SectorRepository,
    private val anomalies: AnomalyRepository,
    private val metrics: MetricsPublisher,
    private val clock: ClockProvider,
) {
    fun execute(event: GateEvent.EntryEvent): GateEventOutcome {
        sessions.findActiveSessionFor(event.licensePlate)?.let { open ->
            return recordDuplicateEntry(event, open.id)
        }

        val occupancy = Occupancy(sessions.countOpenSessions(), sectors.totalCapacity())
        metrics.occupancyObserved(occupancy)

        return when (val admission = OccupancyPolicy.admit(occupancy)) {
            is DomainResult.Failure -> {
                metrics.entryDenied()
                GateEventOutcome.Rejected(admission.error)
            }

            is DomainResult.Success -> {
                val session =
                    ParkingSession.enter(
                        licensePlate = event.licensePlate,
                        entryTime = event.entryTime,
                        occupancyRateAtEntry = occupancy.rate,
                        priceMultiplier = admission.value.multiplier,
                    )

                when (val saved = sessions.save(session)) {
                    is DomainResult.Failure -> recordDuplicateEntry(event, sessionId = null)
                    is DomainResult.Success -> {
                        metrics.pricingTierApplied(admission.value)
                        metrics.occupancyObserved(
                            Occupancy(sessions.countOpenSessions(), sectors.totalCapacity()),
                        )
                        GateEventOutcome.Accepted(session = saved.value, occupancyRate = occupancy.rate)
                    }
                }
            }
        }
    }

    private fun recordDuplicateEntry(
        event: GateEvent.EntryEvent,
        sessionId: Long?,
    ): GateEventOutcome {
        anomalies.record(
            SessionAnomaly(
                type = AnomalyType.DUPLICATE_ENTRY,
                detectedAt = clock.now(),
                licensePlate = event.licensePlate,
                sessionId = sessionId,
                description = "entry received while a session is already open",
            ),
        )
        metrics.anomalyDetected(AnomalyType.DUPLICATE_ENTRY)
        return GateEventOutcome.Ignored(AnomalyType.DUPLICATE_ENTRY, "a session is already open for this plate")
    }
}
