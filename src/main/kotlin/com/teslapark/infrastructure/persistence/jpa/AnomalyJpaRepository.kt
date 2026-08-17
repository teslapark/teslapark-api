package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.SessionAnomalyEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository
interface AnomalyJpaRepository : CrudRepository<SessionAnomalyEntity, Long> {
    fun findByAnomalyTypeOrderById(anomalyType: String): List<SessionAnomalyEntity>

    fun countByAnomalyType(anomalyType: String): Long
}
