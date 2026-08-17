package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.port.AnomalyRepository
import com.teslapark.infrastructure.persistence.entity.SessionAnomalyEntity
import com.teslapark.infrastructure.persistence.jpa.AnomalyJpaRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class JpaAnomalyRepository(
    private val anomalies: AnomalyJpaRepository,
) : AnomalyRepository {
    @Transactional
    override fun record(anomaly: SessionAnomaly): SessionAnomaly {
        val entity = SessionAnomalyEntity()
        entity.sessionId = anomaly.sessionId
        entity.anomalyType = anomaly.type.name
        entity.description = anomaly.description ?: anomaly.licensePlate?.value
        entity.detectedAt = anomaly.detectedAt
        entity.resolved = anomaly.resolved
        return anomaly.copy(id = anomalies.save(entity).id)
    }

    @Transactional
    override fun findAllOfType(type: AnomalyType): List<SessionAnomaly> =
        anomalies.findByAnomalyTypeOrderById(type.name).map { it.toDomain() }

    @Transactional
    override fun countOfType(type: AnomalyType): Int = anomalies.countByAnomalyType(type.name).toInt()
}
