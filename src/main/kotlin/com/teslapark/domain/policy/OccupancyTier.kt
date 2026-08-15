package com.teslapark.domain.policy

import java.math.BigDecimal

enum class OccupancyTier(
    val multiplier: BigDecimal,
    val acceptsEntry: Boolean,
) {
    LOW(BigDecimal("0.900"), acceptsEntry = true),
    NORMAL(BigDecimal("1.000"), acceptsEntry = true),
    HIGH(BigDecimal("1.100"), acceptsEntry = true),
    PEAK(BigDecimal("1.250"), acceptsEntry = true),
    FULL(BigDecimal("1.250"), acceptsEntry = false),
    ;

    companion object {
        private val NORMAL_FLOOR = BigDecimal("0.25")
        private val HIGH_FLOOR = BigDecimal("0.50")
        private val PEAK_FLOOR = BigDecimal("0.75")
        private val FULL_FLOOR = BigDecimal.ONE

        fun of(occupancyRate: BigDecimal): OccupancyTier =
            when {
                occupancyRate >= FULL_FLOOR -> FULL
                occupancyRate >= PEAK_FLOOR -> PEAK
                occupancyRate >= HIGH_FLOOR -> HIGH
                occupancyRate >= NORMAL_FLOOR -> NORMAL
                else -> LOW
            }
    }
}
