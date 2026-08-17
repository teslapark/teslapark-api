package com.teslapark.domain.port

import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.Spot

interface SpotQuery {
    fun findByCoordinates(coordinates: Coordinates): Spot?

    fun countOccupied(): Int
}
