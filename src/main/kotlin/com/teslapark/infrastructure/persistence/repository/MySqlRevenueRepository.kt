package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.RevenueRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import com.teslapark.infrastructure.persistence.mapper.toSectorDailyRevenueEntity
import jakarta.inject.Singleton
import java.sql.Date
import java.time.LocalDate

private const val SELECT_REVENUE =
    """
    SELECT r.sector_id, sec.code AS sector_code, r.revenue_date, r.total_amount,
           r.sessions_count, r.free_sessions_count, r.currency
    FROM sector_daily_revenue r
    JOIN sector sec ON sec.id = r.sector_id
    """

@Singleton
class MySqlRevenueRepository(
    private val jdbc: JdbcOperations,
) : RevenueRepository {
    override fun accumulate(entry: RevenueEntry): DailyRevenue =
        jdbc.inTransaction { connection ->
            connection.update(
                """
                INSERT INTO sector_daily_revenue
                    (sector_id, revenue_date, total_amount, sessions_count, free_sessions_count, currency)
                SELECT sec.id, ?, ?, 1, ?, ? FROM sector sec WHERE sec.code = ?
                ON DUPLICATE KEY UPDATE
                    total_amount = total_amount + VALUES(total_amount),
                    sessions_count = sessions_count + 1,
                    free_sessions_count = free_sessions_count + VALUES(free_sessions_count)
                """.trimIndent(),
                Date.valueOf(entry.revenueDate),
                entry.amount.amount,
                if (entry.isFreeSession) 1 else 0,
                entry.amount.currency.code,
                entry.sectorCode.value,
            )

            findBy(entry.sectorCode, entry.revenueDate)!!
        }

    override fun findBy(
        sectorCode: SectorCode,
        revenueDate: LocalDate,
    ): DailyRevenue? =
        jdbc.readOnly { connection ->
            connection.queryFirst(
                "$SELECT_REVENUE WHERE sec.code = ? AND r.revenue_date = ?",
                sectorCode.value,
                Date.valueOf(revenueDate),
            ) { it.toSectorDailyRevenueEntity().toDomain() }
        }

    override fun findAllOn(revenueDate: LocalDate): List<DailyRevenue> =
        jdbc.readOnly { connection ->
            connection.query(
                "$SELECT_REVENUE WHERE r.revenue_date = ? ORDER BY sec.code",
                Date.valueOf(revenueDate),
            ) { it.toSectorDailyRevenueEntity().toDomain() }
        }
}
