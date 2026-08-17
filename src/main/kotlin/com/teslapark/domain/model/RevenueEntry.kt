package com.teslapark.domain.model

import java.time.LocalDate

data class RevenueEntry(
    val sectorCode: SectorCode,
    val revenueDate: LocalDate,
    val amount: Money,
) {
    val isFreeSession: Boolean get() = amount.isZero
}
