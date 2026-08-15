package com.teslapark.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Occupancy(
    val occupiedSpots: Int,
    val totalCapacity: Int,
) {
    init {
        require(occupiedSpots >= 0) { "occupied spots cannot be negative" }
        require(totalCapacity >= 0) { "total capacity cannot be negative" }
    }

    val rate: BigDecimal =
        if (totalCapacity == 0) {
            ZERO_RATE
        } else {
            BigDecimal(occupiedSpots).divide(BigDecimal(totalCapacity), SCALE, RoundingMode.DOWN)
        }

    val availableSpots: Int get() = (totalCapacity - occupiedSpots).coerceAtLeast(0)

    val isFull: Boolean get() = totalCapacity > 0 && occupiedSpots >= totalCapacity

    companion object {
        const val SCALE = 4

        private val ZERO_RATE: BigDecimal = BigDecimal.ZERO.setScale(SCALE)

        fun empty(totalCapacity: Int): Occupancy = Occupancy(occupiedSpots = 0, totalCapacity = totalCapacity)
    }
}
