package com.teslapark.domain.port

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.Spot

interface SpotRepository {
    fun findByCoordinates(coordinates: Coordinates): Spot?

    fun lockFreeSpotAt(coordinates: Coordinates): Spot?

    fun occupy(
        spot: Spot,
        sessionId: Long,
    ): DomainResult<Spot>

    fun releaseHeldBy(sessionId: Long): DomainResult<Spot>

    fun synchronize(spots: List<Spot>): List<Spot>

    fun countOccupied(): Int
}
