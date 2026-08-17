package com.teslapark

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaMigrationTest {
    private lateinit var jdbcUrl: String
    private var migrationsOnFirstRun = 0

    @BeforeAll
    fun migrateFromScratch() {
        jdbcUrl = MySqlSupport.createIsolatedDatabase("schema_migration_test")
        migrationsOnFirstRun = MySqlSupport.flywayFor(jdbcUrl).migrate().migrationsExecuted
    }

    @Test
    fun `applies the migration on an empty database`() {
        migrationsOnFirstRun shouldBeGreaterThan 0
    }

    @Test
    fun `creates every table of the garage model`() {
        tableNames() shouldContainAll
            listOf(
                "garage",
                "garage_state",
                "sector",
                "parking_spot",
                "vehicle",
                "parking_session",
                "sector_daily_revenue",
                "webhook_event",
                "session_anomaly",
            )
    }

    @Test
    fun `replaying the migration applies nothing`() {
        MySqlSupport.flywayFor(jdbcUrl).migrate().migrationsExecuted shouldBe 0
    }

    @Test
    fun `rejects a second open session for the same plate`() {
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            val sectorId = seedSector(connection, "ACTIVE_PLATE")
            val vehicleId = seedVehicle(connection, "ZUL0001")

            openSession(connection, vehicleId, sectorId, "ZUL0001")

            val violation =
                assertThrows<SQLIntegrityConstraintViolationException> {
                    openSession(connection, vehicleId, sectorId, "ZUL0001")
                }
            violation.message!! shouldContain "uk_parking_session_active_plate"
        }
    }

    @Test
    fun `allows a new session once the previous one exited`() {
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            val sectorId = seedSector(connection, "REENTRY")
            val vehicleId = seedVehicle(connection, "ZUL0002")

            val firstSession = openSession(connection, vehicleId, sectorId, "ZUL0002")
            closeSession(connection, firstSession)

            openSession(connection, vehicleId, sectorId, "ZUL0002") shouldBeGreaterThan firstSession
        }
    }

    @Test
    fun `rejects a duplicated idempotency key`() {
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            val key = "a".repeat(64)
            insertWebhookEvent(connection, key)

            val violation =
                assertThrows<SQLIntegrityConstraintViolationException> {
                    insertWebhookEvent(connection, key)
                }
            violation.message!! shouldContain "uk_webhook_event_idempotency_key"
        }
    }

    @Test
    fun `rejects two spots on the same coordinates`() {
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            val sectorId = seedSector(connection, "COORDS")
            insertSpot(connection, sectorId, externalId = 9001)

            val violation =
                assertThrows<SQLIntegrityConstraintViolationException> {
                    insertSpot(connection, sectorId, externalId = 9002)
                }
            violation.message!! shouldContain "uk_parking_spot_coordinates"
        }
    }

    @Test
    fun `rejects two revenue rows for the same sector and date`() {
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            val sectorId = seedSector(connection, "REVENUE")
            insertDailyRevenue(connection, sectorId)

            val violation =
                assertThrows<SQLIntegrityConstraintViolationException> {
                    insertDailyRevenue(connection, sectorId)
                }
            violation.message!! shouldContain "uk_sector_daily_revenue_sector_date"
        }
    }

    @Test
    fun `keeps decimal money precision on a round trip`() {
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            val sectorId = seedSector(connection, "PRECISION", basePrice = "4.10")

            connection.prepareStatement("SELECT base_price FROM sector WHERE id = ?").use { statement ->
                statement.setLong(1, sectorId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getBigDecimal("base_price").toPlainString() shouldBe "4.10"
                }
            }
        }
    }

    private fun tableNames(): List<String> =
        MySqlSupport.connectionTo(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SHOW TABLES").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
                }
            }
        }

    private fun seedGarage(
        connection: Connection,
        name: String,
    ): Long = insertReturningId(connection, "INSERT INTO garage (name) VALUES ('$name')")

    private fun seedSector(
        connection: Connection,
        code: String,
        basePrice: String = "40.50",
    ): Long {
        val garageId = seedGarage(connection, "garage-$code")
        return insertReturningId(
            connection,
            """
            INSERT INTO sector (garage_id, code, base_price, max_capacity, open_hour, close_hour, duration_limit_minutes)
            VALUES ($garageId, '$code', $basePrice, 10, '00:00', '23:59', 1440)
            """.trimIndent(),
        )
    }

    private fun seedVehicle(
        connection: Connection,
        plate: String,
    ): Long = insertReturningId(connection, "INSERT INTO vehicle (license_plate) VALUES ('$plate')")

    private fun openSession(
        connection: Connection,
        vehicleId: Long,
        sectorId: Long,
        plate: String,
    ): Long =
        insertReturningId(
            connection,
            """
            INSERT INTO parking_session
                (vehicle_id, license_plate, sector_id, status, entry_time, occupancy_rate_at_entry, price_multiplier)
            VALUES ($vehicleId, '$plate', $sectorId, 'ENTERED', CURRENT_TIMESTAMP(3), 0.2000, 0.900)
            """.trimIndent(),
        )

    private fun closeSession(
        connection: Connection,
        sessionId: Long,
    ) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "UPDATE parking_session SET status = 'EXITED', exit_time = CURRENT_TIMESTAMP(3) WHERE id = $sessionId",
            )
        }
    }

    private fun insertWebhookEvent(
        connection: Connection,
        idempotencyKey: String,
    ) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                INSERT INTO webhook_event (idempotency_key, event_type, license_plate, raw_payload)
                VALUES ('$idempotencyKey', 'ENTRY', 'ZUL0003', JSON_OBJECT('event_type', 'ENTRY'))
                """.trimIndent(),
            )
        }
    }

    private fun insertSpot(
        connection: Connection,
        sectorId: Long,
        externalId: Long,
    ) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                INSERT INTO parking_spot (external_id, sector_id, lat, lng)
                VALUES ($externalId, $sectorId, -23.561684, -46.655981)
                """.trimIndent(),
            )
        }
    }

    private fun insertDailyRevenue(
        connection: Connection,
        sectorId: Long,
    ) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                INSERT INTO sector_daily_revenue (sector_id, revenue_date, total_amount)
                VALUES ($sectorId, '2026-08-15', 100.00)
                """.trimIndent(),
            )
        }
    }

    private fun insertReturningId(
        connection: Connection,
        sql: String,
    ): Long =
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                keys.next()
                keys.getLong(1)
            }
        }
}
