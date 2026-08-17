package com.teslapark.domain.model

import java.time.Duration
import java.time.LocalTime

data class Sector(
    val code: SectorCode,
    val basePrice: Money,
    val maxCapacity: Int,
    val openHour: LocalTime,
    val closeHour: LocalTime,
    val durationLimit: Duration,
) {
    init {
        require(maxCapacity >= 0) { "sector $code cannot have negative capacity" }
        require(!durationLimit.isNegative) { "sector $code cannot have a negative duration limit" }
    }

    val durationLimitMinutes: Long get() = durationLimit.toMinutes()

    fun isOpenAt(localTime: LocalTime): Boolean =
        if (openHour <= closeHour) {
            localTime >= openHour && localTime <= closeHour
        } else {
            localTime >= openHour || localTime <= closeHour
        }

    fun exceedsDurationLimit(stay: Duration): Boolean = stay > durationLimit
}
