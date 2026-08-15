package com.teslapark.infrastructure.persistence.entity

import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalTime

class SectorEntity(
    val id: Long,
    val garageId: Long,
    val code: String,
    val basePrice: BigDecimal,
    val maxCapacity: Int,
    val openHour: LocalTime,
    val closeHour: LocalTime,
    val durationLimitMinutes: Int,
)

class SpotEntity(
    val id: Long,
    val externalId: Long,
    val sectorId: Long,
    val sectorCode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val occupied: Boolean,
    val currentSessionId: Long?,
)

class VehicleEntity(
    val id: Long,
    val licensePlate: String,
)

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

class SectorDailyRevenueEntity(
    val sectorId: Long,
    val sectorCode: String,
    val revenueDate: LocalDate,
    val totalAmount: BigDecimal,
    val sessionsCount: Int,
    val freeSessionsCount: Int,
    val currency: String,
)

class WebhookEventEntity(
    val id: Long,
    val idempotencyKey: String,
    val eventType: String,
    val licensePlate: String?,
    val sessionId: Long?,
    val eventTime: Timestamp?,
    val receivedAt: Timestamp,
    val processedAt: Timestamp?,
    val processingStatus: String,
    val rawPayload: String,
)

class SessionAnomalyEntity(
    val id: Long,
    val sessionId: Long?,
    val webhookEventId: Long?,
    val anomalyType: String,
    val description: String?,
    val detectedAt: Timestamp,
    val resolved: Boolean,
)
