package com.teslapark.infrastructure.persistence.entity

import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate

class ParkingSessionEntity(
    val id: Long,
    val vehicleId: Long,
    val licensePlate: String,
    val sectorId: Long?,
    val sectorCode: String?,
    val spotId: Long?,
    val spotExternalId: Long?,
    val status: String,
    val entryTime: Timestamp,
    val parkedTime: Timestamp?,
    val exitTime: Timestamp?,
    val durationMinutes: Int?,
    val basePriceApplied: BigDecimal?,
    val occupancyRateAtEntry: BigDecimal,
    val priceMultiplier: BigDecimal,
    val billedHours: Int?,
    val amountCharged: BigDecimal?,
    val currency: String,
    val revenueDate: LocalDate?,
)
