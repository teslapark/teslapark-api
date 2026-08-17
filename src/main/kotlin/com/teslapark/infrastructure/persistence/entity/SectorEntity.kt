package com.teslapark.infrastructure.persistence.entity

import java.math.BigDecimal
import java.time.LocalTime

class SectorEntity(
    val id: Long,
    val garageId: Long,
    val code: String,
    val basePrice: BigDecimal,
    val maxCapacity: Int,
    val openHour: LocalTime,
    val closeHour: LocalTime,
    val durationLimitMinutes: Int,
)
