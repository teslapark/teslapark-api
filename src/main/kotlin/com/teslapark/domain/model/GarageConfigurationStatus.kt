package com.teslapark.domain.model

enum class GarageConfigurationStatus {
    PENDING,
    SYNCED,
    STALE,
    ;

    val allowsBusinessTraffic: Boolean get() = this != PENDING
}
