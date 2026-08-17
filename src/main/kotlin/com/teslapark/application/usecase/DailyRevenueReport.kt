package com.teslapark.application.usecase

import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.Money
import java.time.LocalDate

data class DailyRevenueReport(
    val revenueDate: LocalDate,
    val total: Money,
    val currency: CurrencyCode,
    val sectors: List<SectorRevenue>,
) {
    val freeSessions: Int get() = sectors.sumOf { it.freeSessions }

    val sessions: Int get() = sectors.sumOf { it.sessions }

    companion object {
        fun empty(
            revenueDate: LocalDate,
            currency: CurrencyCode = CurrencyCode.BRL,
        ): DailyRevenueReport =
            DailyRevenueReport(
                revenueDate = revenueDate,
                total = Money.zero(currency),
                currency = currency,
                sectors = emptyList(),
            )
    }
}
