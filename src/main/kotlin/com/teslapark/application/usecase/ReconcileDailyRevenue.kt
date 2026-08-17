package com.teslapark.application.usecase

import com.teslapark.domain.model.Money
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.domain.port.RevenueRepository
import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class ReconcileDailyRevenue(
    private val revenue: RevenueRepository,
    private val sessions: ParkingSessionRepository,
    private val clock: ClockProvider,
) {
    fun execute(revenueDate: LocalDate? = null): ReconciliationReport {
        val date = revenueDate ?: clock.today()
        val snapshots = revenue.findAllOn(date).associateBy { it.sectorCode }
        val charged = sessions.sumChargedOn(date)

        val discrepancies =
            (snapshots.keys + charged.keys).mapNotNull { sector ->
                val snapshotTotal = snapshots[sector]?.total ?: Money.zero()
                val sessionsTotal = charged[sector] ?: Money.zero()

                if (snapshotTotal.compareTo(sessionsTotal) == 0) {
                    null
                } else {
                    RevenueDiscrepancy(sector, snapshotTotal, sessionsTotal)
                }
            }

        return ReconciliationReport(date, discrepancies.sortedBy { it.sector.value })
    }
}
