package com.teslapark.domain.model

data class Spot(
    val externalId: Long,
    val sectorCode: SectorCode,
    val coordinates: Coordinates,
    val occupied: Boolean = false,
) {
    fun occupy(): Spot = copy(occupied = true)

    fun release(): Spot = copy(occupied = false)
}
