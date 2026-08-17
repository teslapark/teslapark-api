package com.teslapark.infrastructure.persistence.entity

import java.sql.Timestamp

class SessionAnomalyEntity(
    val id: Long,
    val sessionId: Long?,
    val webhookEventId: Long?,
    val anomalyType: String,
    val description: String?,
    val detectedAt: Timestamp,
    val resolved: Boolean,
)
