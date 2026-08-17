package com.teslapark.domain.model

import java.time.Instant

data class SessionAnomaly(
    val type: AnomalyType,
    val detectedAt: Instant,
    val licensePlate: LicensePlate? = null,
    val sessionId: Long? = null,
    val description: String? = null,
    val id: Long? = null,
    val resolved: Boolean = false,
)
