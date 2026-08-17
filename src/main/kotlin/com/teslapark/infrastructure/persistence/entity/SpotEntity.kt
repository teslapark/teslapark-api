package com.teslapark.infrastructure.persistence.entity

import java.math.BigDecimal

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
