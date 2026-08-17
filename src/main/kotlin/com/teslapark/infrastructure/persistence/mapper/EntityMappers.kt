package com.teslapark.infrastructure.persistence.mapper

import com.teslapark.domain.event.GateEventType
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.DailyRevenue
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.ProcessingStatus
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.model.SessionAnomaly
import com.teslapark.domain.model.SessionStatus
import com.teslapark.domain.model.Spot
import com.teslapark.domain.model.WebhookEventRecord
import com.teslapark.infrastructure.persistence.entity.ParkingSessionEntity
import com.teslapark.infrastructure.persistence.entity.SectorDailyRevenueEntity
import com.teslapark.infrastructure.persistence.entity.SectorEntity
import com.teslapark.infrastructure.persistence.entity.SessionAnomalyEntity
import com.teslapark.infrastructure.persistence.entity.SpotEntity
import com.teslapark.infrastructure.persistence.entity.WebhookEventEntity
import java.time.Duration

fun SectorEntity.toDomain(): Sector =
    Sector(
        code = SectorCode(code),
        basePrice = Money.of(basePrice),
        maxCapacity = maxCapacity,
        openHour = openHour,
        closeHour = closeHour,
        durationLimit = Duration.ofMinutes(durationLimitMinutes.toLong()),
    )

fun SpotEntity.toDomain(sectorCode: String): Spot =
    Spot(
        externalId = externalId,
        sectorCode = SectorCode(sectorCode),
        coordinates = Coordinates.of(latitude, longitude),
        occupied = occupied,
    )

fun ParkingSessionEntity.toDomain(
    sectorCode: String?,
    spotExternalId: Long?,
): ParkingSession =
    ParkingSession(
        id = id,
        licensePlate = LicensePlate(licensePlate),
        status = SessionStatus.valueOf(status),
        entryTime = entryTime,
        occupancyRateAtEntry = occupancyRateAtEntry,
        priceMultiplier = priceMultiplier,
        sectorCode = sectorCode?.let { SectorCode(it) },
        spotExternalId = spotExternalId,
        parkedTime = parkedTime,
        exitTime = exitTime,
        basePriceApplied = basePriceApplied?.let { Money.of(it, CurrencyCode(currency)) },
        billedHours = billedHours,
        amountCharged = amountCharged?.let { Money.of(it, CurrencyCode(currency)) },
        revenueDate = revenueDate,
    )

fun SectorDailyRevenueEntity.toDomain(sectorCode: String): DailyRevenue =
    DailyRevenue(
        sectorCode = SectorCode(sectorCode),
        revenueDate = revenueDate,
        total = Money.of(totalAmount, CurrencyCode(currency)),
        sessionsCount = sessionsCount,
        freeSessionsCount = freeSessionsCount,
    )

fun WebhookEventEntity.toDomain(): WebhookEventRecord =
    WebhookEventRecord(
        id = id,
        idempotencyKey = IdempotencyKey(idempotencyKey),
        eventType = GateEventType.valueOf(eventType),
        receivedAt = receivedAt,
        rawPayload = rawPayload,
        licensePlate = licensePlate?.let { LicensePlate(it) },
        eventTime = eventTime,
        sessionId = sessionId,
        processedAt = processedAt,
        status = ProcessingStatus.valueOf(processingStatus),
    )

fun SessionAnomalyEntity.toDomain(): SessionAnomaly =
    SessionAnomaly(
        id = id,
        type = AnomalyType.valueOf(anomalyType),
        detectedAt = detectedAt,
        sessionId = sessionId,
        description = description,
        resolved = resolved,
    )
