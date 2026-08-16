package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.policy.PricingPolicy
import com.teslapark.domain.port.AnomalyRepository
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.MetricsPublisher
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.domain.port.RevenueRepository
import com.teslapark.domain.port.SectorRepository
import com.teslapark.domain.port.SpotRepository
import jakarta.inject.Singleton

@Singleton
class HandleExitEvent(
    private val sessions: ParkingSessionRepository,
    private val sectors: SectorRepository,
    private val spots: SpotRepository,
    private val revenue: RevenueRepository,
    private val anomalies: AnomalyRepository,
    private val metrics: MetricsPublisher,
    private val clock: ClockProvider,
) {
    fun execute(event: GateEvent.ExitEvent): GateEventOutcome {
        val session =
            sessions.findActiveSessionFor(event.licensePlate)
                ?: return recordAnomaly(event, AnomalyType.EXIT_WITHOUT_ENTRY, "no open session for this plate")

        val exited =
            when (val transition = session.exit(event.exitTime)) {
                is DomainResult.Failure -> return rejectOrIgnore(event, transition.error)
                is DomainResult.Success -> transition.value
            }

        val billingSector =
            billingSectorFor(exited.sectorCode?.value)
                ?: return recordAnomaly(event, AnomalyType.OUT_OF_ORDER_EVENT, "garage has no sector to bill against")

        val charge = PricingPolicy.charge(exited.stay!!, billingSector.basePrice, exited.priceMultiplier)
        val revenueDate = clock.localDateOf(event.exitTime)
        val charged =
            exited
                .copy(sectorCode = billingSector.code)
                .withCharge(billingSector.basePrice, charge.chargeableHours, charge.amount, revenueDate)

        session.id?.let { spots.releaseHeldBy(it) }

        return when (val saved = sessions.save(charged)) {
            is DomainResult.Failure -> GateEventOutcome.Rejected(saved.error)
            is DomainResult.Success -> {
                revenue.accumulate(RevenueEntry(billingSector.code, revenueDate, charge.amount))
                metrics.revenueCollected(billingSector.code, charge.amount)
                metrics.sessionCompleted(exited.stay!!)
                metrics.occupancyObserved(
                    com.teslapark.domain.model
                        .Occupancy(sessions.countOpenSessions(), sectors.totalCapacity()),
                )
                GateEventOutcome.Accepted(session = saved.value, charge = charge)
            }
        }
    }

    private fun billingSectorFor(sectorCode: String?): Sector? =
        sectorCode?.let { code -> sectors.findAll().firstOrNull { it.code.value == code } }
            ?: sectors.findAll().minByOrNull { it.basePrice.amount }

    private fun rejectOrIgnore(
        event: GateEvent.ExitEvent,
        error: DomainError,
    ): GateEventOutcome =
        when (error) {
            is DomainError.ExitTimeBeforeEntryTime -> GateEventOutcome.Rejected(error)
            else -> recordAnomaly(event, AnomalyType.OUT_OF_ORDER_EVENT, error.toString())
        }

    private fun recordAnomaly(
        event: GateEvent.ExitEvent,
        type: AnomalyType,
        detail: String,
    ): GateEventOutcome {
        anomalies.record(
            SessionAnomaly(
                type = type,
                detectedAt = clock.now(),
                licensePlate = event.licensePlate,
                description = detail,
            ),
        )
        metrics.anomalyDetected(type)
        return GateEventOutcome.Ignored(type, detail)
    }
}
