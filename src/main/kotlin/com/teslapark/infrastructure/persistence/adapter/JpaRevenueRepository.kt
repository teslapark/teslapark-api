package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.RevenueRepository
import com.teslapark.infrastructure.persistence.jpa.RevenueJpaRepository
import com.teslapark.infrastructure.persistence.jpa.SectorJpaRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.LocalDate

@Singleton
open class JpaRevenueRepository(
    private val revenue: RevenueJpaRepository,
    private val sectors: SectorJpaRepository,
) : RevenueRepository {
    @Transactional
    override fun accumulate(entry: RevenueEntry): DailyRevenue {
        revenue.accumulate(
            sectorCode = entry.sectorCode.value,
            revenueDate = entry.revenueDate,
            amount = entry.amount.amount,
            freeSession = if (entry.isFreeSession) 1 else 0,
            currency = entry.amount.currency.code,
        )
        return findBy(entry.sectorCode, entry.revenueDate)!!
    }

    @Transactional
    override fun findBy(
        sectorCode: SectorCode,
        revenueDate: LocalDate,
    ): DailyRevenue? {
        val sectorId = sectors.findByCode(sectorCode.value)?.id ?: return null
        return revenue.findBySectorIdAndRevenueDate(sectorId, revenueDate)?.toDomain(sectorCode.value)
    }

    @Transactional
    override fun findAllOn(revenueDate: LocalDate): List<DailyRevenue> =
        revenue
            .findByRevenueDate(revenueDate)
            .mapNotNull { snapshot ->
                sectors.findById(snapshot.sectorId).orElse(null)?.let { snapshot.toDomain(it.code) }
            }.sortedBy { it.sectorCode.value }
}
