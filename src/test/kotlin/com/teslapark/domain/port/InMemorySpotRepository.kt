package com.teslapark.domain.port

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.Spot

class InMemorySpotRepository : SpotRepository {
    private val spots = linkedMapOf<Long, Spot>()
    private val holders = mutableMapOf<Long, Long>()
    private val locked = mutableSetOf<Long>()

    override fun findByCoordinates(coordinates: Coordinates): Spot? = spots.values.firstOrNull { it.coordinates == coordinates }

    override fun lockFreeSpotAt(coordinates: Coordinates): Spot? =
        lockFree { findByCoordinates(coordinates)?.takeUnless { spot -> spot.occupied } }

    override fun lockAnyFreeSpot(): Spot? = lockFree { spots.values.firstOrNull { spot -> !spot.occupied && spot.externalId !in locked } }

    private fun lockFree(select: () -> Spot?): Spot? =
        synchronized(locked) {
            val candidate = select() ?: return null
            if (!locked.add(candidate.externalId)) return null
            candidate
        }

    override fun occupy(
        spot: Spot,
        sessionId: Long,
    ): DomainResult<Spot> {
        val stored =
            spots[spot.externalId]
                ?: return DomainError
                    .SpotNotFound(
                        spot.coordinates.latitude.toPlainString(),
                        spot.coordinates.longitude.toPlainString(),
                    ).asFailure()
        if (stored.occupied) return DomainError.SpotAlreadyOccupied(stored.externalId).asFailure()

        val occupied = stored.occupy()
        spots[occupied.externalId] = occupied
        holders[sessionId] = occupied.externalId
        locked.remove(occupied.externalId)
        return occupied.asSuccess()
    }

    override fun releaseHeldBy(sessionId: Long): DomainResult<Spot> {
        val externalId = holders.remove(sessionId) ?: return DomainError.SpotNotHeld(sessionId).asFailure()
        val released = spots.getValue(externalId).release()
        spots[externalId] = released
        locked.remove(externalId)
        return released.asSuccess()
    }

    override fun synchronize(spots: List<Spot>): List<Spot> {
        spots.forEach { incoming ->
            val current = this.spots[incoming.externalId]
            this.spots[incoming.externalId] = incoming.copy(occupied = current?.occupied ?: incoming.occupied)
        }
        return this.spots.values.toList()
    }

    override fun countOccupied(): Int = spots.values.count { it.occupied }
}
