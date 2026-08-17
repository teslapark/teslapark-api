package com.teslapark.domain.port

import com.teslapark.domain.model.Sector

interface SectorSynchronization {
    fun synchronize(sectors: List<Sector>): List<Sector>
}
