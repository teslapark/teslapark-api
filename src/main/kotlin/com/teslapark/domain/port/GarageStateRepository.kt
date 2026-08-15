package com.teslapark.domain.port

import com.teslapark.domain.model.GarageConfigurationStatus
import java.time.Instant

interface GarageStateRepository {
    fun currentStatus(): GarageConfigurationStatus

    fun lastSyncAt(): Instant?

    fun totalCapacity(): Int

    fun markSynced(
        at: Instant,
        totalCapacity: Int,
    )

    fun markStale()
}
