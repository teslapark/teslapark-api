package com.teslapark.domain.port

import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode

class InMemorySectorRepository : SectorRepository {
    private val sectors = linkedMapOf<SectorCode, Sector>()

    override fun findByCode(code: SectorCode): Sector? = sectors[code]

    override fun findAll(): List<Sector> = sectors.values.toList()

    override fun synchronize(sectors: List<Sector>): List<Sector> {
        sectors.forEach { this.sectors[it.code] = it }
        return findAll()
    }

    override fun totalCapacity(): Int = sectors.values.sumOf { it.maxCapacity }
}
