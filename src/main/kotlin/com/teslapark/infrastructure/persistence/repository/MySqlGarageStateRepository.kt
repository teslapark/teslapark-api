package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.GarageStateRepository
import com.teslapark.infrastructure.persistence.mapper.toTimestamp
import jakarta.inject.Singleton
import java.time.Instant

@Singleton
class MySqlGarageStateRepository(
    private val jdbc: JdbcOperations,
    private val garageRegistry: GarageRegistry,
) : GarageStateRepository {
    override fun currentStatus(): GarageConfigurationStatus =
        jdbc.readOnly { connection ->
            connection.queryFirst("SELECT config_status FROM garage_state") {
                GarageConfigurationStatus.valueOf(it.getString("config_status"))
            }
        } ?: GarageConfigurationStatus.PENDING

    override fun lastSyncAt(): Instant? =
        jdbc
            .readOnly { connection ->
                connection.queryFirst("SELECT last_sync_at FROM garage_state") { it.getTimestamp("last_sync_at") }
            }?.toInstant()

    override fun totalCapacity(): Int =
        jdbc.readOnly { connection ->
            connection.queryFirst("SELECT total_capacity FROM garage_state") { it.getInt("total_capacity") }
        } ?: 0

    override fun markSynced(
        at: Instant,
        totalCapacity: Int,
    ) {
        jdbc.inTransaction { connection ->
            val garageId = garageRegistry.ensureGarage(connection)
            connection.update(
                """
                INSERT INTO garage_state (garage_id, total_capacity, config_status, last_sync_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_capacity = VALUES(total_capacity),
                    config_status = VALUES(config_status),
                    last_sync_at = VALUES(last_sync_at),
                    version = version + 1
                """.trimIndent(),
                garageId,
                totalCapacity,
                GarageConfigurationStatus.SYNCED.name,
                at.toTimestamp(),
            )
        }
    }

    override fun markStale() {
        jdbc.inTransaction { connection ->
            connection.update(
                "UPDATE garage_state SET config_status = ?, version = version + 1 WHERE config_status = ?",
                GarageConfigurationStatus.STALE.name,
                GarageConfigurationStatus.SYNCED.name,
            )
        }
    }
}
