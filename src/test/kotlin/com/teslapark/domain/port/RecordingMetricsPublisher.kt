package com.teslapark.domain.port

import com.teslapark.domain.event.EventResult
import com.teslapark.domain.event.GateEventType
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Occupancy
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.policy.OccupancyTier
import java.time.Duration

class RecordingMetricsPublisher : MetricsPublisher {
    val webhookEvents = mutableListOf<Pair<GateEventType, EventResult>>()
    val anomalies = mutableListOf<AnomalyType>()
    val tiers = mutableListOf<OccupancyTier>()
    val revenues = mutableListOf<Pair<SectorCode, Money>>()
    val stays = mutableListOf<Duration>()
    var deniedEntries: Int = 0
        private set
    var lastOccupancy: Occupancy? = null
        private set

    override fun webhookEventReceived(
        eventType: GateEventType,
        result: EventResult,
    ) {
        webhookEvents += eventType to result
    }

    override fun entryDenied() {
        deniedEntries++
    }

    override fun pricingTierApplied(tier: OccupancyTier) {
        tiers += tier
    }

    override fun sessionCompleted(stay: Duration) {
        stays += stay
    }

    override fun revenueCollected(
        sector: SectorCode,
        amount: Money,
    ) {
        revenues += sector to amount
    }

    override fun anomalyDetected(type: AnomalyType) {
        anomalies += type
    }

    override fun occupancyObserved(occupancy: Occupancy) {
        lastOccupancy = occupancy
    }
}
