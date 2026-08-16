package com.teslapark.infrastructure.metrics

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class HttpLatencyHistogramFilter : MeterFilter {
    override fun configure(
        id: Meter.Id,
        config: DistributionStatisticConfig,
    ): DistributionStatisticConfig =
        if (id.name == HTTP_SERVER_REQUESTS) {
            DistributionStatisticConfig
                .builder()
                .percentilesHistogram(true)
                .serviceLevelObjectives(*SERVICE_LEVEL_OBJECTIVES)
                .build()
                .merge(config)
        } else {
            config
        }

    private companion object {
        const val HTTP_SERVER_REQUESTS = "http.server.requests"

        val SERVICE_LEVEL_OBJECTIVES =
            listOf(
                Duration.ofMillis(25),
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
            ).map { it.toNanos().toDouble() }.toDoubleArray()
    }
}
