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
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

fun SectorEntity.toDomain(): Sector =
    Sector(
        code = SectorCode(code),
        basePrice = Money.of(basePrice),
        maxCapacity = maxCapacity,
        openHour = openHour,
        closeHour = closeHour,
        durationLimit = Duration.ofMinutes(durationLimitMinutes.toLong()),
    )

fun SpotEntity.toDomain(): Spot =
    Spot(
        externalId = externalId,
        sectorCode = SectorCode(sectorCode),
        coordinates = Coordinates.of(latitude, longitude),
        occupied = occupied,
    )

fun ParkingSessionEntity.toDomain(): ParkingSession =
    ParkingSession(
        id = id,
        licensePlate = LicensePlate(licensePlate),
        status = SessionStatus.valueOf(status),
        entryTime = entryTime.toInstant(),
        occupancyRateAtEntry = occupancyRateAtEntry,
        priceMultiplier = priceMultiplier,
        sectorCode = sectorCode?.let { SectorCode(it) },
        spotExternalId = spotExternalId,
        parkedTime = parkedTime?.toInstant(),
        exitTime = exitTime?.toInstant(),
        basePriceApplied = basePriceApplied?.let { Money.of(it, CurrencyCode(currency)) },
        billedHours = billedHours,
        amountCharged = amountCharged?.let { Money.of(it, CurrencyCode(currency)) },
        revenueDate = revenueDate,
    )

fun SectorDailyRevenueEntity.toDomain(): DailyRevenue =
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
        receivedAt = receivedAt.toInstant(),
        rawPayload = rawPayload,
        licensePlate = licensePlate?.let { LicensePlate(it) },
        eventTime = eventTime?.toInstant(),
        sessionId = sessionId,
        processedAt = processedAt?.toInstant(),
        status = ProcessingStatus.valueOf(processingStatus),
    )

fun SessionAnomalyEntity.toDomain(): SessionAnomaly =
    SessionAnomaly(
        id = id,
        type = AnomalyType.valueOf(anomalyType),
        detectedAt = detectedAt.toInstant(),
        licensePlate = null,
        sessionId = sessionId,
        description = description,
        resolved = resolved,
    )

fun ResultSet.toSectorEntity(): SectorEntity =
    SectorEntity(
        id = getLong("id"),
        garageId = getLong("garage_id"),
        code = getString("code"),
        basePrice = getBigDecimal("base_price"),
        maxCapacity = getInt("max_capacity"),
        openHour = getTime("open_hour").toLocalTime(),
        closeHour = getTime("close_hour").toLocalTime(),
        durationLimitMinutes = getInt("duration_limit_minutes"),
    )

fun ResultSet.toSpotEntity(): SpotEntity =
    SpotEntity(
        id = getLong("id"),
        externalId = getLong("external_id"),
        sectorId = getLong("sector_id"),
        sectorCode = getString("sector_code"),
        latitude = getBigDecimal("lat"),
        longitude = getBigDecimal("lng"),
        occupied = getBoolean("occupied"),
        currentSessionId = getNullableLong("current_session_id"),
    )

fun ResultSet.toParkingSessionEntity(): ParkingSessionEntity =
    ParkingSessionEntity(
        id = getLong("id"),
        vehicleId = getLong("vehicle_id"),
        licensePlate = getString("license_plate"),
        sectorId = getNullableLong("sector_id"),
        sectorCode = getString("sector_code"),
        spotId = getNullableLong("spot_id"),
        spotExternalId = getNullableLong("spot_external_id"),
        status = getString("status"),
        entryTime = getTimestamp("entry_time"),
        parkedTime = getTimestamp("parked_time"),
        exitTime = getTimestamp("exit_time"),
        durationMinutes = getNullableInt("duration_minutes"),
        basePriceApplied = getBigDecimal("base_price_applied"),
        occupancyRateAtEntry = getBigDecimal("occupancy_rate_at_entry"),
        priceMultiplier = getBigDecimal("price_multiplier"),
        billedHours = getNullableInt("billed_hours"),
        amountCharged = getBigDecimal("amount_charged"),
        currency = getString("currency"),
        revenueDate = getDate("revenue_date")?.toLocalDate(),
    )

fun ResultSet.toSectorDailyRevenueEntity(): SectorDailyRevenueEntity =
    SectorDailyRevenueEntity(
        sectorId = getLong("sector_id"),
        sectorCode = getString("sector_code"),
        revenueDate = getDate("revenue_date").toLocalDate(),
        totalAmount = getBigDecimal("total_amount"),
        sessionsCount = getInt("sessions_count"),
        freeSessionsCount = getInt("free_sessions_count"),
        currency = getString("currency"),
    )

fun ResultSet.toWebhookEventEntity(): WebhookEventEntity =
    WebhookEventEntity(
        id = getLong("id"),
        idempotencyKey = getString("idempotency_key"),
        eventType = getString("event_type"),
        licensePlate = getString("license_plate"),
        sessionId = getNullableLong("session_id"),
        eventTime = getTimestamp("event_time"),
        receivedAt = getTimestamp("received_at"),
        processedAt = getTimestamp("processed_at"),
        processingStatus = getString("processing_status"),
        rawPayload = getString("raw_payload"),
    )

fun ResultSet.toSessionAnomalyEntity(): SessionAnomalyEntity =
    SessionAnomalyEntity(
        id = getLong("id"),
        sessionId = getNullableLong("session_id"),
        webhookEventId = getNullableLong("webhook_event_id"),
        anomalyType = getString("anomaly_type"),
        description = getString("description"),
        detectedAt = getTimestamp("detected_at"),
        resolved = getBoolean("resolved"),
    )

fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)

private fun ResultSet.getNullableLong(column: String): Long? = getLong(column).takeUnless { wasNull() }

private fun ResultSet.getNullableInt(column: String): Int? = getInt(column).takeUnless { wasNull() }
