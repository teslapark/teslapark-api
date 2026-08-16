package com.teslapark.infrastructure.metrics

import com.teslapark.domain.event.GateEventType
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Occupancy
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.policy.OccupancyTier
import com.teslapark.domain.port.EventResult
import com.teslapark.domain.port.MetricsPublisher
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.DoubleAdder

@Singleton
class MicrometerMetricsPublisher(
    private val registry: MeterRegistry,
) : MetricsPublisher {
    private val occupiedSpots = AtomicInteger()
    private val totalCapacity = AtomicInteger()
    private val occupancyRate = DoubleAdder()

    init {
        Gauge
            .builder(OCCUPANCY_RATE, occupancyRate) { it.sum() }
            .description("Share of the garage currently occupied")
            .register(registry)

        Gauge
            .builder(OCCUPIED_SPOTS, occupiedSpots) { it.get().toDouble() }
            .description("Spots currently occupied")
            .register(registry)

        Gauge
            .builder(TOTAL_CAPACITY, totalCapacity) { it.get().toDouble() }
            .description("Total capacity synchronized from the gate control system")
            .register(registry)

        preRegisterSeries()
    }

    private fun preRegisterSeries() {
        registry.counter(ENTRIES_DENIED)
        registry.summary(SESSION_DURATION)

        OccupancyTier.entries.forEach { tier -> registry.counter(PRICING_MULTIPLIER, "tier", tier.name) }
        AnomalyType.entries.forEach { type -> registry.counter(SESSION_ANOMALIES, "type", type.name) }
        GateEventType.entries.forEach { eventType ->
            EventResult.entries.forEach { result ->
                registry.counter(WEBHOOK_EVENTS, "event_type", eventType.name, "result", result.name.lowercase())
            }
        }
    }

    override fun webhookEventReceived(
        eventType: GateEventType,
        result: EventResult,
    ) {
        registry
            .counter(WEBHOOK_EVENTS, "event_type", eventType.name, "result", result.name.lowercase())
            .increment()
    }

    override fun entryDenied() {
        registry.counter(ENTRIES_DENIED).increment()
    }

    override fun pricingTierApplied(tier: OccupancyTier) {
        registry.counter(PRICING_MULTIPLIER, "tier", tier.name).increment()
    }

    override fun sessionCompleted(stay: Duration) {
        registry
            .summary(SESSION_DURATION)
            .record(stay.toMinutes().toDouble())
    }

    override fun revenueCollected(
        sector: SectorCode,
        amount: Money,
    ) {
        registry.counter(REVENUE_TOTAL, "sector", sector.value).increment(amount.amount.toDouble())
    }

    override fun anomalyDetected(type: AnomalyType) {
        registry.counter(SESSION_ANOMALIES, "type", type.name).increment()
    }

    override fun occupancyObserved(occupancy: Occupancy) {
        occupiedSpots.set(occupancy.occupiedSpots)
        totalCapacity.set(occupancy.totalCapacity)
        occupancyRate.reset()
        occupancyRate.add(occupancy.rate.toDouble())
    }

    private companion object {
        const val OCCUPANCY_RATE = "garage.occupancy.rate"
        const val OCCUPIED_SPOTS = "garage.occupied.spots"
        const val TOTAL_CAPACITY = "garage.total.capacity"
        const val ENTRIES_DENIED = "parking.entries.denied"
        const val REVENUE_TOTAL = "parking.revenue.total"
        const val SESSION_DURATION = "parking.session.duration.minutes"
        const val PRICING_MULTIPLIER = "pricing.multiplier.applied"
        const val WEBHOOK_EVENTS = "webhook.events"
        const val SESSION_ANOMALIES = "session.anomalies"
    }
}
