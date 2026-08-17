package com.teslapark.domain.port

import com.teslapark.domain.model.GarageConfigurationStatus
import java.time.Instant

class InMemoryGarageStateRepository : GarageStateRepository {
    private var status = GarageConfigurationStatus.PENDING
    private var syncedAt: Instant? = null
    private var capacity = 0

    override fun currentStatus(): GarageConfigurationStatus = status

    override fun lastSyncAt(): Instant? = syncedAt

    override fun totalCapacity(): Int = capacity

    override fun markSynced(
        at: Instant,
        totalCapacity: Int,
    ) {
        status = GarageConfigurationStatus.SYNCED
        syncedAt = at
        capacity = totalCapacity
    }

    override fun markStale() {
        if (status == GarageConfigurationStatus.SYNCED) status = GarageConfigurationStatus.STALE
    }
}
