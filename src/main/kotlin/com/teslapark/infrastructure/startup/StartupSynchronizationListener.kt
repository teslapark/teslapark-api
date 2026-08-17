package com.teslapark.infrastructure.startup

import com.teslapark.application.usecase.SyncGarageConfiguration
import com.teslapark.domain.error.DomainResult
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
@Requires(property = "teslapark.garage.sync.enabled", notEquals = "false")
class StartupSynchronizationListener(
    private val syncGarageConfiguration: SyncGarageConfiguration,
) : ApplicationEventListener<StartupEvent> {
    private val logger = LoggerFactory.getLogger(StartupSynchronizationListener::class.java)

    override fun onApplicationEvent(event: StartupEvent) {
        synchronizeQuietly()
    }

    @Scheduled(
        fixedDelay = "\${teslapark.garage.sync.retry-delay:30s}",
        initialDelay = "\${teslapark.garage.sync.retry-delay:30s}",
    )
    fun retryUntilSynchronized() {
        if (syncGarageConfiguration.isSynchronized()) return
        synchronizeQuietly()
    }

    private fun synchronizeQuietly() {
        val outcome = runCatching { syncGarageConfiguration.execute() }

        outcome.onFailure { failure ->
            logger.warn("garage synchronization attempt failed: {}", failure.message)
        }
        outcome.onSuccess { result ->
            when (result) {
                is DomainResult.Success ->
                    logger.info(
                        "garage synchronized: {} sectors, {} spots, capacity {}",
                        result.value.sectors,
                        result.value.spots,
                        result.value.totalCapacity,
                    )

                is DomainResult.Failure ->
                    logger.warn("garage configuration unavailable, readiness stays down: {}", result.error)
            }
        }
    }
}
