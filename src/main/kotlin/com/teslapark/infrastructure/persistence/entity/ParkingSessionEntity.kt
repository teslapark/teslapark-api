package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "parking_session")
class ParkingSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "vehicle_id", nullable = false)
    var vehicleId: Long = 0

    @Column(name = "license_plate", nullable = false)
    lateinit var licensePlate: String

    @Column(name = "sector_id")
    var sectorId: Long? = null

    @Column(name = "spot_id")
    var spotId: Long? = null

    @Column(name = "status", nullable = false)
    lateinit var status: String

    @Column(name = "entry_time", nullable = false)
    lateinit var entryTime: Instant

    @Column(name = "parked_time")
    var parkedTime: Instant? = null

    @Column(name = "exit_time")
    var exitTime: Instant? = null

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null

    @Column(name = "base_price_applied")
    var basePriceApplied: BigDecimal? = null

    @Column(name = "occupancy_rate_at_entry", nullable = false)
    lateinit var occupancyRateAtEntry: BigDecimal

    @Column(name = "price_multiplier", nullable = false)
    lateinit var priceMultiplier: BigDecimal

    @Column(name = "billed_hours")
    var billedHours: Int? = null

    @Column(name = "amount_charged")
    var amountCharged: BigDecimal? = null

    @Column(name = "currency", nullable = false)
    var currency: String = "BRL"

    @Column(name = "revenue_date")
    var revenueDate: LocalDate? = null

    @Column(name = "version", nullable = false)
    var version: Long = 0

    @Column(name = "active_plate", insertable = false, updatable = false)
    var activePlate: String? = null
}
