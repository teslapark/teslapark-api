package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.port.AnomalyRepository
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.MetricsPublisher
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.domain.port.SpotRepository
import jakarta.inject.Singleton

@Singleton
class HandleParkedEvent(
    private val sessions: ParkingSessionRepository,
    private val spots: SpotRepository,
    private val anomalies: AnomalyRepository,
    private val metrics: MetricsPublisher,
    private val clock: ClockProvider,
) {
    fun execute(event: GateEvent.ParkedEvent): GateEventOutcome {
        val session =
            sessions.findActiveSessionFor(event.licensePlate)
                ?: return recordAnomaly(event, AnomalyType.OUT_OF_ORDER_EVENT, "no open session for this plate", null)

        val known = spots.findByCoordinates(event.coordinates)
        if (known == null) {
            return recordAnomaly(event, AnomalyType.PARKED_UNKNOWN_SPOT, "no spot matches the coordinates", session.id)
        }

        val free =
            spots.lockFreeSpotAt(event.coordinates)
                ?: return recordAnomaly(event, AnomalyType.OUT_OF_ORDER_EVENT, "spot is already occupied", session.id)

        return when (val parked = session.park(free, clock.now())) {
            is DomainResult.Failure ->
                recordAnomaly(event, AnomalyType.OUT_OF_ORDER_EVENT, parked.error.toString(), session.id)

            is DomainResult.Success -> {
                spots.occupy(free, session.id!!)
                when (val saved = sessions.save(parked.value)) {
                    is DomainResult.Failure -> GateEventOutcome.Rejected(saved.error)
                    is DomainResult.Success -> GateEventOutcome.Accepted(session = saved.value)
                }
            }
        }
    }

    private fun recordAnomaly(
        event: GateEvent.ParkedEvent,
        type: AnomalyType,
        detail: String,
        sessionId: Long?,
    ): GateEventOutcome {
        anomalies.record(
            SessionAnomaly(
                type = type,
                detectedAt = clock.now(),
                licensePlate = event.licensePlate,
                sessionId = sessionId,
                description = detail,
            ),
        )
        metrics.anomalyDetected(type)
        return GateEventOutcome.Ignored(type, detail)
    }
}
