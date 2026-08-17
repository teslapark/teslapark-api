package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.GarageEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository
interface GarageJpaRepository : CrudRepository<GarageEntity, Long> {
    fun findByName(name: String): GarageEntity?

    @Query(
        value = """
            INSERT INTO garage (name, timezone, currency) VALUES (:name, :timezone, :currency)
            ON DUPLICATE KEY UPDATE timezone = VALUES(timezone), currency = VALUES(currency)
        """,
        nativeQuery = true,
    )
    fun upsert(
        name: String,
        timezone: String,
        currency: String,
    ): Int
}
