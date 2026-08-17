package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.GarageStateEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.time.Instant

@Repository
interface GarageStateJpaRepository : CrudRepository<GarageStateEntity, Long> {
    @Query(
        value = """
            INSERT INTO garage_state (garage_id, total_capacity, config_status, last_sync_at)
            VALUES (:garageId, :totalCapacity, :configStatus, :lastSyncAt)
            ON DUPLICATE KEY UPDATE
                total_capacity = VALUES(total_capacity),
                config_status = VALUES(config_status),
                last_sync_at = VALUES(last_sync_at),
                version = version + 1
        """,
        nativeQuery = true,
    )
    fun markSynced(
        garageId: Long,
        totalCapacity: Int,
        configStatus: String,
        lastSyncAt: Instant,
    ): Int

    @Query(
        value = "UPDATE garage_state SET config_status = :stale, version = version + 1 WHERE config_status = :synced",
        nativeQuery = true,
    )
    fun markStale(
        stale: String,
        synced: String,
    ): Int
}
