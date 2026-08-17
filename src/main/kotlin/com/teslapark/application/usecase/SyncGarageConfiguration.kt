package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.GarageConfigurationProvider
import com.teslapark.domain.port.GarageStateRepository
import com.teslapark.domain.port.SectorSynchronization
import com.teslapark.domain.port.SpotSynchronization
import jakarta.inject.Singleton

@Singleton
class SyncGarageConfiguration(
    private val configurationProvider: GarageConfigurationProvider,
    private val sectors: SectorSynchronization,
    private val spots: SpotSynchronization,
    private val garageState: GarageStateRepository,
    private val clock: ClockProvider,
) {
    fun execute(): DomainResult<GarageSynchronizationSummary> {
        val statusBefore = garageState.currentStatus()

        return when (val fetched = configurationProvider.fetchConfiguration()) {
            is DomainResult.Failure -> {
                if (statusBefore == GarageConfigurationStatus.SYNCED) garageState.markStale()
                fetched
            }

            is DomainResult.Success -> {
                val configuration = fetched.value
                val storedSectors = sectors.synchronize(configuration.garage.sectors)
                val storedSpots = spots.synchronize(configuration.spots)
                val syncedAt = clock.now()

                garageState.markSynced(syncedAt, configuration.totalCapacity)

                GarageSynchronizationSummary(
                    status = GarageConfigurationStatus.SYNCED,
                    sectors = storedSectors.size,
                    spots = storedSpots.size,
                    totalCapacity = configuration.totalCapacity,
                    syncedAt = syncedAt,
                    firstSynchronization = statusBefore == GarageConfigurationStatus.PENDING,
                ).asSuccess()
            }
        }
    }

    fun isSynchronized(): Boolean = garageState.currentStatus() == GarageConfigurationStatus.SYNCED
}
