package com.teslapark.infrastructure.persistence.entity

import java.sql.Timestamp

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
