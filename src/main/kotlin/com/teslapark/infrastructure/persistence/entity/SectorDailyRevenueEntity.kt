package com.teslapark.infrastructure.persistence.entity

import java.math.BigDecimal
import java.time.LocalDate

class SectorDailyRevenueEntity(
    val sectorId: Long,
    val sectorCode: String,
    val revenueDate: LocalDate,
    val totalAmount: BigDecimal,
    val sessionsCount: Int,
    val freeSessionsCount: Int,
    val currency: String,
)
