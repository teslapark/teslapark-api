package com.teslapark.application.usecase

import com.teslapark.domain.model.GarageConfigurationStatus
import java.time.Instant

data class GarageSynchronizationSummary(
    val status: GarageConfigurationStatus,
    val sectors: Int,
    val spots: Int,
    val totalCapacity: Int,
    val syncedAt: Instant,
    val firstSynchronization: Boolean,
)
