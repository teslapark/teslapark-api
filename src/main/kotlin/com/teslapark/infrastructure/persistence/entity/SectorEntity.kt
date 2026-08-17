package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalTime

@Entity
@Table(name = "sector")
class SectorEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "garage_id", nullable = false)
    var garageId: Long = 0

    @Column(name = "code", nullable = false)
    lateinit var code: String

    @Column(name = "base_price", nullable = false)
    lateinit var basePrice: BigDecimal

    @Column(name = "max_capacity", nullable = false)
    var maxCapacity: Int = 0

    @Column(name = "open_hour", nullable = false)
    lateinit var openHour: LocalTime

    @Column(name = "close_hour", nullable = false)
    lateinit var closeHour: LocalTime

    @Column(name = "duration_limit_minutes", nullable = false)
    var durationLimitMinutes: Int = 0
}
