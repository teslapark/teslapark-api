package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.SpotEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.math.BigDecimal

@Repository
interface SpotJpaRepository : CrudRepository<SpotEntity, Long> {
    fun findByExternalId(externalId: Long): SpotEntity?

    fun findByLatitudeAndLongitude(
        latitude: BigDecimal,
        longitude: BigDecimal,
    ): SpotEntity?

    fun findByCurrentSessionId(currentSessionId: Long): SpotEntity?

    fun countByOccupiedTrue(): Long

    @Query(
        value = """
            SELECT * FROM parking_spot
            WHERE lat = :latitude AND lng = :longitude AND occupied = FALSE
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockFreeSpotAt(
        latitude: BigDecimal,
        longitude: BigDecimal,
    ): SpotEntity?

    @Query(
        value = "SELECT * FROM parking_spot WHERE occupied = FALSE ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun lockAnyFreeSpot(): SpotEntity?

    @Query(
        value = """
            UPDATE parking_spot SET occupied = TRUE, current_session_id = :sessionId
            WHERE external_id = :externalId AND occupied = FALSE
        """,
        nativeQuery = true,
    )
    fun occupy(
        externalId: Long,
        sessionId: Long,
    ): Int

    @Query(
        value = """
            INSERT INTO parking_spot (external_id, sector_id, lat, lng)
            SELECT :externalId, sec.id, :latitude, :longitude FROM sector sec WHERE sec.code = :sectorCode
            ON DUPLICATE KEY UPDATE sector_id = VALUES(sector_id), lat = VALUES(lat), lng = VALUES(lng)
        """,
        nativeQuery = true,
    )
    fun upsert(
        externalId: Long,
        sectorCode: String,
        latitude: BigDecimal,
        longitude: BigDecimal,
    ): Int
}
