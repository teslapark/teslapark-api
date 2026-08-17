package com.teslapark.domain.port

import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.SectorCode
import java.time.LocalDate

interface RevenueRepository {
    fun accumulate(entry: RevenueEntry): DailyRevenue

    fun findBy(
        sectorCode: SectorCode,
        revenueDate: LocalDate,
    ): DailyRevenue?

    fun findAllOn(revenueDate: LocalDate): List<DailyRevenue>
}
