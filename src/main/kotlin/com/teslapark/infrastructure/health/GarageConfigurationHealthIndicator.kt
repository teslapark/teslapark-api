package com.teslapark.infrastructure.health

import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.GarageStateRepository
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.management.health.indicator.annotation.Readiness
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Readiness
@Singleton
class GarageConfigurationHealthIndicator(
    private val garageState: GarageStateRepository,
) : HealthIndicator {
    override fun getResult(): Publisher<HealthResult> {
        val status = runCatching { garageState.currentStatus() }.getOrDefault(GarageConfigurationStatus.PENDING)
        val health = if (status.allowsBusinessTraffic) HealthStatus.UP else HealthStatus.DOWN

        return Publishers.just(
            HealthResult
                .builder(NAME, health)
                .details(mapOf("configStatus" to status.name))
                .build(),
        )
    }

    private companion object {
        const val NAME = "garageConfiguration"
    }
}
