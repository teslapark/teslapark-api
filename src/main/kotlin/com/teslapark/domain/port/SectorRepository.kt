package com.teslapark.domain.port

import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode

interface SectorRepository {
    fun findByCode(code: SectorCode): Sector?

    fun findAll(): List<Sector>

    fun synchronize(sectors: List<Sector>): List<Sector>

    fun totalCapacity(): Int
}
