package com.teslapark.infrastructure.health

import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.management.health.indicator.annotation.Readiness
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Readiness
@Singleton
class DatabaseHealthIndicator(
    private val probe: DatabaseProbe,
) : HealthIndicator {
    override fun getResult(): Publisher<HealthResult> {
        val reachable = runCatching { probe.isReachable() }.getOrDefault(false)

        val builder =
            if (reachable) {
                HealthResult.builder(NAME, HealthStatus.UP)
            } else {
                HealthResult.builder(NAME, HealthStatus.DOWN).details(mapOf("error" to "database is unreachable"))
            }

        return Publishers.just(builder.build())
    }

    private companion object {
        const val NAME = "database"
    }
}
