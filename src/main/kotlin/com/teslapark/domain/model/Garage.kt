package com.teslapark.domain.model

import java.time.ZoneId

data class Garage(
    val name: String,
    val timezone: ZoneId,
    val currency: CurrencyCode,
    val sectors: List<Sector>,
) {
    val totalCapacity: Int = sectors.sumOf { it.maxCapacity }

    fun sectorBy(code: SectorCode): Sector? = sectors.firstOrNull { it.code == code }

    fun occupancyOf(occupiedSpots: Int): Occupancy = Occupancy(occupiedSpots, totalCapacity)

    companion object {
        val DEFAULT_TIMEZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}
