package com.teslapark.domain.port

import com.teslapark.domain.model.Spot

interface SpotSynchronization {
    fun synchronize(spots: List<Spot>): List<Spot>
}
