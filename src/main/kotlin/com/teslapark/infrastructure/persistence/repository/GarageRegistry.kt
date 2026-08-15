package com.teslapark.infrastructure.persistence.repository

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.sql.Connection

@Singleton
class GarageRegistry(
    @Value("\${teslapark.garage.name:sp-01}") private val garageName: String,
    @Value("\${teslapark.garage.timezone:America/Sao_Paulo}") private val timezone: String,
    @Value("\${teslapark.garage.currency:BRL}") private val currency: String,
) {
    fun ensureGarage(connection: Connection): Long {
        connection.update(
            """
            INSERT INTO garage (name, timezone, currency) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE timezone = VALUES(timezone), currency = VALUES(currency)
            """.trimIndent(),
            garageName,
            timezone,
            currency,
        )
        return connection.queryFirst("SELECT id FROM garage WHERE name = ?", garageName) { it.getLong("id") }!!
    }
}
