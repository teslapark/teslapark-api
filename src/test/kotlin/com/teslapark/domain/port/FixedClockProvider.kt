package com.teslapark.domain.port

import java.time.Instant
import java.time.ZoneId

class FixedClockProvider(
    private var instant: Instant,
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
) : ClockProvider {
    override fun now(): Instant = instant

    override fun operatingZone(): ZoneId = zone

    fun advanceTo(next: Instant) {
        instant = next
    }
}
