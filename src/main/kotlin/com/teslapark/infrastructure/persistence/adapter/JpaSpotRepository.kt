package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.Spot
import com.teslapark.domain.port.SpotRepository
import com.teslapark.infrastructure.persistence.entity.SpotEntity
import com.teslapark.infrastructure.persistence.jpa.SectorJpaRepository
import com.teslapark.infrastructure.persistence.jpa.SpotJpaRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class JpaSpotRepository(
    private val spots: SpotJpaRepository,
    private val sectors: SectorJpaRepository,
) : SpotRepository {
    @Transactional
    override fun findByCoordinates(coordinates: Coordinates): Spot? =
        spots.findByLatitudeAndLongitude(coordinates.latitude, coordinates.longitude)?.toSpot()

    @Transactional
    override fun lockFreeSpotAt(coordinates: Coordinates): Spot? =
        spots.lockFreeSpotAt(coordinates.latitude, coordinates.longitude)?.toSpot()

    @Transactional
    override fun lockAnyFreeSpot(): Spot? = spots.lockAnyFreeSpot()?.toSpot()

    @Transactional
    override fun occupy(
        spot: Spot,
        sessionId: Long,
    ): DomainResult<Spot> =
        if (spots.occupy(spot.externalId, sessionId) == 0) {
            DomainError.SpotAlreadyOccupied(spot.externalId).asFailure()
        } else {
            spot.occupy().asSuccess()
        }

    @Transactional
    override fun releaseHeldBy(sessionId: Long): DomainResult<Spot> {
        val held = spots.findByCurrentSessionId(sessionId) ?: return DomainError.SpotNotHeld(sessionId).asFailure()
        held.occupied = false
        held.currentSessionId = null
        spots.update(held)
        return held.toSpot().release().asSuccess()
    }

    @Transactional
    override fun synchronize(spots: List<Spot>): List<Spot> {
        spots.forEach { spot ->
            this.spots.upsert(spot.externalId, spot.sectorCode.value, spot.coordinates.latitude, spot.coordinates.longitude)
        }
        return this.spots
            .findAll()
            .sortedBy { it.externalId }
            .map { it.toSpot() }
    }

    @Transactional
    override fun countOccupied(): Int = spots.countByOccupiedTrue().toInt()

    private fun SpotEntity.toSpot(): Spot = toDomain(sectors.findById(sectorId).orElseThrow().code)
}
