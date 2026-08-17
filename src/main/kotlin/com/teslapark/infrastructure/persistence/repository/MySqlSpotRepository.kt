package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.Spot
import com.teslapark.domain.port.SpotRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import com.teslapark.infrastructure.persistence.mapper.toSpotEntity
import jakarta.inject.Singleton
import java.sql.Connection

private const val SPOT_HELD_BY_SESSION = "uk_parking_spot_current_session"

private const val SELECT_SPOT =
    """
    SELECT s.id, s.external_id, s.sector_id, sec.code AS sector_code, s.lat, s.lng, s.occupied, s.current_session_id
    FROM parking_spot s
    JOIN sector sec ON sec.id = s.sector_id
    """

@Singleton
class MySqlSpotRepository(
    private val jdbc: JdbcOperations,
) : SpotRepository {
    override fun findByCoordinates(coordinates: Coordinates): Spot? =
        jdbc.readOnly { connection ->
            connection.queryFirst(
                "$SELECT_SPOT WHERE s.lat = ? AND s.lng = ?",
                coordinates.latitude,
                coordinates.longitude,
            ) { it.toSpotEntity().toDomain() }
        }

    override fun lockFreeSpotAt(coordinates: Coordinates): Spot? =
        jdbc.inTransaction { connection ->
            connection.queryFirst(
                "$SELECT_SPOT WHERE s.lat = ? AND s.lng = ? AND s.occupied = FALSE FOR UPDATE OF s SKIP LOCKED",
                coordinates.latitude,
                coordinates.longitude,
            ) { it.toSpotEntity().toDomain() }
        }

    override fun lockAnyFreeSpot(): Spot? =
        jdbc.inTransaction { connection ->
            connection.queryFirst(
                "$SELECT_SPOT WHERE s.occupied = FALSE ORDER BY s.id LIMIT 1 FOR UPDATE OF s SKIP LOCKED",
            ) { it.toSpotEntity().toDomain() }
        }

    override fun occupy(
        spot: Spot,
        sessionId: Long,
    ): DomainResult<Spot> =
        translateConstraintViolation(
            expected = mapOf(SPOT_HELD_BY_SESSION to DomainError.SpotAlreadyOccupied(spot.externalId)),
            block = {
                jdbc.inTransaction { connection ->
                    val updated =
                        connection.update(
                            """
                            UPDATE parking_spot
                            SET occupied = TRUE, current_session_id = ?
                            WHERE external_id = ? AND occupied = FALSE
                            """.trimIndent(),
                            sessionId,
                            spot.externalId,
                        )

                    if (updated == 0) {
                        DomainError.SpotAlreadyOccupied(spot.externalId).asFailure()
                    } else {
                        spot.occupy().asSuccess()
                    }
                }
            },
        )

    override fun releaseHeldBy(sessionId: Long): DomainResult<Spot> =
        jdbc.inTransaction { connection ->
            val held =
                connection.queryFirst("$SELECT_SPOT WHERE s.current_session_id = ?", sessionId) {
                    it.toSpotEntity().toDomain()
                } ?: return@inTransaction DomainError.SpotNotHeld(sessionId).asFailure()

            connection.update(
                "UPDATE parking_spot SET occupied = FALSE, current_session_id = NULL WHERE external_id = ?",
                held.externalId,
            )
            held.release().asSuccess()
        }

    override fun synchronize(spots: List<Spot>): List<Spot> {
        jdbc.inTransaction { connection ->
            spots.forEach { spot -> upsert(connection, spot) }
        }
        return jdbc.readOnly { connection ->
            connection.query("$SELECT_SPOT ORDER BY s.external_id") { it.toSpotEntity().toDomain() }
        }
    }

    override fun countOccupied(): Int =
        jdbc.readOnly { connection ->
            connection.queryFirst("SELECT COUNT(*) AS occupied FROM parking_spot WHERE occupied = TRUE") {
                it.getInt("occupied")
            } ?: 0
        }

    private fun upsert(
        connection: Connection,
        spot: Spot,
    ) {
        connection.update(
            """
            INSERT INTO parking_spot (external_id, sector_id, lat, lng)
            SELECT ?, sec.id, ?, ? FROM sector sec WHERE sec.code = ?
            ON DUPLICATE KEY UPDATE sector_id = VALUES(sector_id), lat = VALUES(lat), lng = VALUES(lng)
            """.trimIndent(),
            spot.externalId,
            spot.coordinates.latitude,
            spot.coordinates.longitude,
            spot.sectorCode.value,
        )
    }
}
