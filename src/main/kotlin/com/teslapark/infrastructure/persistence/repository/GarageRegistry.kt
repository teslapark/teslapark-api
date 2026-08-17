package com.teslapark.infrastructure.persistence.repository

import com.teslapark.infrastructure.config.GarageConfigurationProperties
import jakarta.inject.Singleton
import java.sql.Connection

@Singleton
class GarageRegistry(
    private val garage: GarageConfigurationProperties,
) {
    fun ensureGarage(connection: Connection): Long {
        connection.update(
            """
            INSERT INTO garage (name, timezone, currency) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE timezone = VALUES(timezone), currency = VALUES(currency)
            """.trimIndent(),
            garage.name,
            garage.timezone,
            garage.currency,
        )
        return connection.queryFirst("SELECT id FROM garage WHERE name = ?", garage.name) { it.getLong("id") }!!
    }
}
