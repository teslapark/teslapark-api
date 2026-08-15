package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import com.teslapark.infrastructure.persistence.mapper.toParkingSessionEntity
import com.teslapark.infrastructure.persistence.mapper.toTimestamp
import jakarta.inject.Singleton
import java.sql.Connection
import java.time.Duration

private const val SELECT_SESSION =
    """
    SELECT ps.id, ps.vehicle_id, ps.license_plate, ps.sector_id, sec.code AS sector_code,
           ps.spot_id, sp.external_id AS spot_external_id, ps.status, ps.entry_time, ps.parked_time,
           ps.exit_time, ps.duration_minutes, ps.base_price_applied, ps.occupancy_rate_at_entry,
           ps.price_multiplier, ps.billed_hours, ps.amount_charged, ps.currency, ps.revenue_date
    FROM parking_session ps
    LEFT JOIN sector sec ON sec.id = ps.sector_id
    LEFT JOIN parking_spot sp ON sp.id = ps.spot_id
    """

@Singleton
class MySqlParkingSessionRepository(
    private val jdbc: JdbcOperations,
) : ParkingSessionRepository {
    override fun findActiveSessionFor(licensePlate: LicensePlate): ParkingSession? =
        jdbc.readOnly { connection ->
            connection.queryFirst("$SELECT_SESSION WHERE ps.active_plate = ?", licensePlate.value) {
                it.toParkingSessionEntity().toDomain()
            }
        }

    override fun findById(id: Long): ParkingSession? =
        jdbc.readOnly { connection ->
            connection.queryFirst("$SELECT_SESSION WHERE ps.id = ?", id) { it.toParkingSessionEntity().toDomain() }
        }

    override fun save(session: ParkingSession): DomainResult<ParkingSession> =
        translateConstraintViolation(
            block = { jdbc.inTransaction { connection -> persist(connection, session) } },
            onViolation = { DomainError.SessionAlreadyOpen(session.licensePlate.value) },
        )

    override fun countOpenSessions(): Int =
        jdbc.readOnly { connection ->
            connection.queryFirst("SELECT COUNT(*) AS open_sessions FROM parking_session WHERE active_plate IS NOT NULL") {
                it.getInt("open_sessions")
            } ?: 0
        }

    private fun persist(
        connection: Connection,
        session: ParkingSession,
    ): DomainResult<ParkingSession> =
        if (session.id == null) {
            insert(connection, session).asSuccess()
        } else {
            update(connection, session).asSuccess()
        }

    private fun insert(
        connection: Connection,
        session: ParkingSession,
    ): ParkingSession {
        val vehicleId = ensureVehicle(connection, session.licensePlate)
        val id =
            connection.insertReturningId(
                """
                INSERT INTO parking_session
                    (vehicle_id, license_plate, sector_id, status, entry_time, occupancy_rate_at_entry, price_multiplier, currency)
                VALUES (?, ?, (SELECT id FROM sector WHERE code = ?), ?, ?, ?, ?, ?)
                """.trimIndent(),
                vehicleId,
                session.licensePlate.value,
                session.sectorCode?.value,
                session.status.name,
                session.entryTime.toTimestamp(),
                session.occupancyRateAtEntry,
                session.priceMultiplier,
                session.basePriceApplied?.currency?.code ?: "BRL",
            )
        return session.copy(id = id)
    }

    private fun update(
        connection: Connection,
        session: ParkingSession,
    ): ParkingSession {
        connection.update(
            """
            UPDATE parking_session SET
                status = ?,
                sector_id = COALESCE((SELECT id FROM sector WHERE code = ?), sector_id),
                spot_id = COALESCE((SELECT id FROM parking_spot WHERE external_id = ?), spot_id),
                parked_time = ?,
                exit_time = ?,
                duration_minutes = ?,
                base_price_applied = ?,
                billed_hours = ?,
                amount_charged = ?,
                revenue_date = ?,
                version = version + 1
            WHERE id = ?
            """.trimIndent(),
            session.status.name,
            session.sectorCode?.value,
            session.spotExternalId,
            session.parkedTime?.toTimestamp(),
            session.exitTime?.toTimestamp(),
            session.stay?.let(Duration::toMinutes)?.toInt(),
            session.basePriceApplied?.amount,
            session.billedHours,
            session.amountCharged?.amount,
            null,
            session.id,
        )
        return session
    }

    private fun ensureVehicle(
        connection: Connection,
        licensePlate: LicensePlate,
    ): Long {
        connection.update(
            """
            INSERT INTO vehicle (license_plate) VALUES (?)
            ON DUPLICATE KEY UPDATE last_seen_at = CURRENT_TIMESTAMP(3)
            """.trimIndent(),
            licensePlate.value,
        )
        return connection.queryFirst("SELECT id FROM vehicle WHERE license_plate = ?", licensePlate.value) {
            it.getLong("id")
        }!!
    }
}
