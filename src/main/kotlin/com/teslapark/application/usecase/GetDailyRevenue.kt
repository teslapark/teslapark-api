package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.RevenueRepository
import com.teslapark.domain.port.SectorQuery
import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class GetDailyRevenue(
    private val revenue: RevenueRepository,
    private val sectors: SectorQuery,
    private val clock: ClockProvider,
) {
    fun execute(
        revenueDate: LocalDate?,
        sectorCode: SectorCode?,
    ): DomainResult<DailyRevenueReport> {
        val date = revenueDate ?: clock.today()

        if (sectorCode == null) return reportOf(date, revenue.findAllOn(date)).asSuccess()

        sectors.findByCode(sectorCode) ?: return DomainError.SectorNotFound(sectorCode.value).asFailure()

        val snapshot = revenue.findBy(sectorCode, date)
        return reportOf(date, listOfNotNull(snapshot)).asSuccess()
    }

    private fun reportOf(
        date: LocalDate,
        snapshots: List<DailyRevenue>,
    ): DailyRevenueReport {
        if (snapshots.isEmpty()) return DailyRevenueReport.empty(date)

        val currency = snapshots.first().total.currency
        return DailyRevenueReport(
            revenueDate = date,
            total = snapshots.map { it.total }.reduce(Money::plus),
            currency = currency,
            sectors =
                snapshots
                    .sortedBy { it.sectorCode.value }
                    .map { SectorRevenue(it.sectorCode, it.total, it.sessionsCount, it.freeSessionsCount) },
        )
    }
}
