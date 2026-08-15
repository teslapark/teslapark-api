package com.teslapark.domain.model

data class GarageConfiguration(
    val garage: Garage,
    val spots: List<Spot>,
) {
    val totalCapacity: Int get() = garage.totalCapacity

    fun spotsOf(sectorCode: SectorCode): List<Spot> = spots.filter { it.sectorCode == sectorCode }
}
