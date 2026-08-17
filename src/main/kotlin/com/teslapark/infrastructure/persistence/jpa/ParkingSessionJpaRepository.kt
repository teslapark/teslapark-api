package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.ParkingSessionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.math.BigDecimal
import java.time.Instant

@Repository
interface ParkingSessionJpaRepository : CrudRepository<ParkingSessionEntity, Long> {
    fun findByActivePlate(activePlate: String): ParkingSessionEntity?

    @Query("SELECT COUNT(s) FROM ParkingSessionEntity s WHERE s.activePlate IS NOT NULL")
    fun countOpenSessions(): Long

    @Query(
        value = """
            INSERT IGNORE INTO parking_session
                (vehicle_id, license_plate, status, entry_time, occupancy_rate_at_entry, price_multiplier, currency)
            VALUES (:vehicleId, :licensePlate, :status, :entryTime, :occupancyRate, :priceMultiplier, :currency)
        """,
        nativeQuery = true,
    )
    @Suppress("LongParameterList")
    fun insertIfPlateIsFree(
        vehicleId: Long,
        licensePlate: String,
        status: String,
        entryTime: Instant,
        occupancyRate: BigDecimal,
        priceMultiplier: BigDecimal,
        currency: String,
    ): Int
}
