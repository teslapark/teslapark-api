package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.VehicleEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository
interface VehicleJpaRepository : CrudRepository<VehicleEntity, Long> {
    fun findByLicensePlate(licensePlate: String): VehicleEntity?

    @Query(
        value = """
            INSERT INTO vehicle (license_plate) VALUES (:licensePlate)
            ON DUPLICATE KEY UPDATE last_seen_at = CURRENT_TIMESTAMP(3)
        """,
        nativeQuery = true,
    )
    fun touch(licensePlate: String): Int
}
