package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.port.AnomalyRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import com.teslapark.infrastructure.persistence.mapper.toSessionAnomalyEntity
import com.teslapark.infrastructure.persistence.mapper.toTimestamp
import jakarta.inject.Singleton

private const val SELECT_ANOMALY =
    "SELECT id, session_id, webhook_event_id, anomaly_type, description, detected_at, resolved FROM session_anomaly"

@Singleton
class MySqlAnomalyRepository(
    private val jdbc: JdbcOperations,
) : AnomalyRepository {
    override fun record(anomaly: SessionAnomaly): SessionAnomaly =
        jdbc.inTransaction { connection ->
            val id =
                connection.insertReturningId(
                    """
                    INSERT INTO session_anomaly (session_id, anomaly_type, description, detected_at, resolved)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    anomaly.sessionId,
                    anomaly.type.name,
                    anomaly.description ?: anomaly.licensePlate?.value,
                    anomaly.detectedAt.toTimestamp(),
                    anomaly.resolved,
                )
            anomaly.copy(id = id)
        }

    override fun findAllOfType(type: AnomalyType): List<SessionAnomaly> =
        jdbc.readOnly { connection ->
            connection.query("$SELECT_ANOMALY WHERE anomaly_type = ? ORDER BY id", type.name) {
                it.toSessionAnomalyEntity().toDomain()
            }
        }

    override fun countOfType(type: AnomalyType): Int =
        jdbc.readOnly { connection ->
            connection.queryFirst("SELECT COUNT(*) AS total FROM session_anomaly WHERE anomaly_type = ?", type.name) {
                it.getInt("total")
            } ?: 0
        }
}
