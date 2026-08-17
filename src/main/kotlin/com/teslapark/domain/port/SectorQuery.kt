package com.teslapark.domain.port

import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode

interface SectorQuery {
    fun findByCode(code: SectorCode): Sector?

    fun findAll(): List<Sector>

    fun totalCapacity(): Int
}
