package com.teslapark.domain.port

import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.SectorCode
import java.time.LocalDate

class InMemoryRevenueRepository : RevenueRepository {
    private val snapshots = linkedMapOf<Pair<SectorCode, LocalDate>, DailyRevenue>()

    override fun accumulate(entry: RevenueEntry): DailyRevenue {
        val key = entry.sectorCode to entry.revenueDate
        val current = snapshots[key] ?: DailyRevenue.empty(entry.sectorCode, entry.revenueDate)
        val updated = current.accumulate(entry)
        snapshots[key] = updated
        return updated
    }

    override fun findBy(
        sectorCode: SectorCode,
        revenueDate: LocalDate,
    ): DailyRevenue? = snapshots[sectorCode to revenueDate]

    override fun findAllOn(revenueDate: LocalDate): List<DailyRevenue> = snapshots.values.filter { it.revenueDate == revenueDate }
}
