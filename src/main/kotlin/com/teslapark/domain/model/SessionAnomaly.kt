package com.teslapark.domain.model

import java.time.Instant

enum class AnomalyType {
    EXIT_WITHOUT_ENTRY,
    DUPLICATE_ENTRY,
    PARKED_UNKNOWN_SPOT,
    OUT_OF_ORDER_EVENT,
}

data class SessionAnomaly(
    val type: AnomalyType,
    val detectedAt: Instant,
    val licensePlate: LicensePlate? = null,
    val sessionId: Long? = null,
    val description: String? = null,
    val id: Long? = null,
    val resolved: Boolean = false,
)
