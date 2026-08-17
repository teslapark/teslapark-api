package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.GarageStateRepository
import com.teslapark.infrastructure.persistence.jpa.GarageStateJpaRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant

@Singleton
open class JpaGarageStateRepository(
    private val states: GarageStateJpaRepository,
    private val garageRegistry: GarageRegistry,
) : GarageStateRepository {
    @Transactional
    override fun currentStatus(): GarageConfigurationStatus =
        current()?.let { GarageConfigurationStatus.valueOf(it.configStatus) } ?: GarageConfigurationStatus.PENDING

    @Transactional
    override fun lastSyncAt(): Instant? = current()?.lastSyncAt

    @Transactional
    override fun totalCapacity(): Int = current()?.totalCapacity ?: 0

    @Transactional
    override fun markSynced(
        at: Instant,
        totalCapacity: Int,
    ) {
        states.markSynced(
            garageId = garageRegistry.ensureGarage(),
            totalCapacity = totalCapacity,
            configStatus = GarageConfigurationStatus.SYNCED.name,
            lastSyncAt = at,
        )
    }

    @Transactional
    override fun markStale() {
        states.markStale(GarageConfigurationStatus.STALE.name, GarageConfigurationStatus.SYNCED.name)
    }

    private fun current() = states.findAll().firstOrNull()
}
