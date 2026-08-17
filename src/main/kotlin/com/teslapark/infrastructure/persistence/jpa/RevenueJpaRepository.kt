package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.SectorDailyRevenueEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface RevenueJpaRepository : CrudRepository<SectorDailyRevenueEntity, Long> {
    fun findBySectorIdAndRevenueDate(
        sectorId: Long,
        revenueDate: LocalDate,
    ): SectorDailyRevenueEntity?

    fun findByRevenueDate(revenueDate: LocalDate): List<SectorDailyRevenueEntity>

    @Query(
        value = """
            INSERT INTO sector_daily_revenue
                (sector_id, revenue_date, total_amount, sessions_count, free_sessions_count, currency)
            SELECT sec.id, :revenueDate, :amount, 1, :freeSession, :currency FROM sector sec WHERE sec.code = :sectorCode
            ON DUPLICATE KEY UPDATE
                total_amount = total_amount + VALUES(total_amount),
                sessions_count = sessions_count + 1,
                free_sessions_count = free_sessions_count + VALUES(free_sessions_count)
        """,
        nativeQuery = true,
    )
    fun accumulate(
        sectorCode: String,
        revenueDate: LocalDate,
        amount: BigDecimal,
        freeSession: Int,
        currency: String,
    ): Int
}
