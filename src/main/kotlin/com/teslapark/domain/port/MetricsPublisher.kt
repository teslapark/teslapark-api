package com.teslapark.domain.port

import com.teslapark.domain.event.GateEventType
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Occupancy
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.policy.OccupancyTier
import java.time.Duration

enum class EventResult {
    PROCESSED,
    DUPLICATE,
    IGNORED,
    REJECTED,
}

interface MetricsPublisher {
    fun webhookEventReceived(
        eventType: GateEventType,
        result: EventResult,
    )

    fun entryDenied()

    fun pricingTierApplied(tier: OccupancyTier)

    fun sessionCompleted(stay: Duration)

    fun revenueCollected(
        sector: SectorCode,
        amount: Money,
    )

    fun anomalyDetected(type: AnomalyType)

    fun occupancyObserved(occupancy: Occupancy)
}
