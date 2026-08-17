package com.teslapark.infrastructure.time

import com.teslapark.domain.port.ClockProvider
import com.teslapark.infrastructure.config.GarageConfigurationProperties
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Singleton
class SystemClockProvider(
    garage: GarageConfigurationProperties,
) : ClockProvider {
    private val zone: ZoneId = garage.zone
    private val clock: Clock = Clock.system(zone)

    override fun now(): Instant = clock.instant()

    override fun operatingZone(): ZoneId = zone
}
