package com.teslapark.infrastructure.time

import com.teslapark.domain.port.ClockProvider
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Singleton
class SystemClockProvider(
    @Value("\${teslapark.garage.timezone:America/Sao_Paulo}") timezone: String,
) : ClockProvider {
    private val zone: ZoneId = ZoneId.of(timezone)
    private val clock: Clock = Clock.system(zone)

    override fun now(): Instant = clock.instant()

    override fun operatingZone(): ZoneId = zone
}
