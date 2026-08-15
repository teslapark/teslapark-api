package com.teslapark.domain.port

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

interface ClockProvider {
    fun now(): Instant

    fun operatingZone(): ZoneId

    fun localDateOf(instant: Instant): LocalDate = instant.atZone(operatingZone()).toLocalDate()

    fun localTimeOf(instant: Instant): LocalTime = instant.atZone(operatingZone()).toLocalTime()

    fun today(): LocalDate = localDateOf(now())
}
