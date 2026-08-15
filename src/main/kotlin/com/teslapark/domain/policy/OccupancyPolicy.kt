package com.teslapark.domain.policy

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.Occupancy

object OccupancyPolicy {
    fun tierFor(occupancy: Occupancy): OccupancyTier = OccupancyTier.of(occupancy.rate)

    fun admitsEntry(occupancy: Occupancy): Boolean = tierFor(occupancy).acceptsEntry

    fun admit(occupancy: Occupancy): DomainResult<OccupancyTier> {
        val tier = tierFor(occupancy)
        return if (tier.acceptsEntry) tier.asSuccess() else DomainError.GarageFull.asFailure()
    }
}
