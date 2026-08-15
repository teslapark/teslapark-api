package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.SectorRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import com.teslapark.infrastructure.persistence.mapper.toSectorEntity
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.sql.Connection
import java.sql.Time

private const val SELECT_SECTOR =
    """
    SELECT id, garage_id, code, base_price, max_capacity, open_hour, close_hour, duration_limit_minutes
    FROM sector
    """

@Singleton
class MySqlSectorRepository(
    private val jdbc: JdbcOperations,
    @Value("\${teslapark.garage.name:sp-01}") private val garageName: String,
) : SectorRepository {
    override fun findByCode(code: SectorCode): Sector? =
        jdbc.readOnly { connection ->
            connection.queryFirst("$SELECT_SECTOR WHERE code = ?", code.value) { it.toSectorEntity().toDomain() }
        }

    override fun findAll(): List<Sector> =
        jdbc.readOnly { connection ->
            connection.query("$SELECT_SECTOR ORDER BY code") { it.toSectorEntity().toDomain() }
        }

    override fun synchronize(sectors: List<Sector>): List<Sector> {
        jdbc.inTransaction { connection ->
            val garageId = ensureGarage(connection)
            sectors.forEach { sector -> upsert(connection, garageId, sector) }
        }
        return findAll()
    }

    override fun totalCapacity(): Int =
        jdbc.readOnly { connection ->
            connection.queryFirst("SELECT COALESCE(SUM(max_capacity), 0) AS capacity FROM sector") {
                it.getInt("capacity")
            } ?: 0
        }

    fun findIdByCode(
        connection: Connection,
        code: SectorCode,
    ): Long? = connection.queryFirst("SELECT id FROM sector WHERE code = ?", code.value) { it.getLong("id") }

    private fun ensureGarage(connection: Connection): Long {
        connection.update(
            "INSERT INTO garage (name) VALUES (?) ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3)",
            garageName,
        )
        return connection.queryFirst("SELECT id FROM garage WHERE name = ?", garageName) { it.getLong("id") }!!
    }

    private fun upsert(
        connection: Connection,
        garageId: Long,
        sector: Sector,
    ) {
        connection.update(
            """
            INSERT INTO sector (garage_id, code, base_price, max_capacity, open_hour, close_hour, duration_limit_minutes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                base_price = VALUES(base_price),
                max_capacity = VALUES(max_capacity),
                open_hour = VALUES(open_hour),
                close_hour = VALUES(close_hour),
                duration_limit_minutes = VALUES(duration_limit_minutes)
            """.trimIndent(),
            garageId,
            sector.code.value,
            sector.basePrice.amount,
            sector.maxCapacity,
            Time.valueOf(sector.openHour),
            Time.valueOf(sector.closeHour),
            sector.durationLimitMinutes.toInt(),
        )
    }
}
