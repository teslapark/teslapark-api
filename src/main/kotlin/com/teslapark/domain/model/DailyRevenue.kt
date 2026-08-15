package com.teslapark.domain.model

import java.time.LocalDate

data class RevenueEntry(
    val sectorCode: SectorCode,
    val revenueDate: LocalDate,
    val amount: Money,
) {
    val isFreeSession: Boolean get() = amount.isZero
}

data class DailyRevenue(
    val sectorCode: SectorCode,
    val revenueDate: LocalDate,
    val total: Money,
    val sessionsCount: Int,
    val freeSessionsCount: Int,
) {
    fun accumulate(entry: RevenueEntry): DailyRevenue =
        copy(
            total = total + entry.amount,
            sessionsCount = sessionsCount + 1,
            freeSessionsCount = freeSessionsCount + if (entry.isFreeSession) 1 else 0,
        )

    companion object {
        fun empty(
            sectorCode: SectorCode,
            revenueDate: LocalDate,
            currency: CurrencyCode = CurrencyCode.BRL,
        ): DailyRevenue =
            DailyRevenue(
                sectorCode = sectorCode,
                revenueDate = revenueDate,
                total = Money.zero(currency),
                sessionsCount = 0,
                freeSessionsCount = 0,
            )
    }
}
