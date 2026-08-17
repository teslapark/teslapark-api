package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "sector_daily_revenue")
class SectorDailyRevenueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "sector_id", nullable = false)
    var sectorId: Long = 0

    @Column(name = "revenue_date", nullable = false)
    lateinit var revenueDate: LocalDate

    @Column(name = "total_amount", nullable = false)
    lateinit var totalAmount: BigDecimal

    @Column(name = "sessions_count", nullable = false)
    var sessionsCount: Int = 0

    @Column(name = "free_sessions_count", nullable = false)
    var freeSessionsCount: Int = 0

    @Column(name = "currency", nullable = false)
    var currency: String = "BRL"
}
