package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.SectorEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository
interface SectorJpaRepository : CrudRepository<SectorEntity, Long> {
    fun findByCode(code: String): SectorEntity?

    fun listOrderByCode(): List<SectorEntity>

    @Query("SELECT COALESCE(SUM(s.maxCapacity), 0) FROM SectorEntity s")
    fun totalCapacity(): Long
}
