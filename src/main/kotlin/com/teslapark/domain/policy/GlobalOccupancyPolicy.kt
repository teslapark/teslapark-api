package com.teslapark.domain.policy

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.Occupancy
import jakarta.inject.Singleton

@Singleton
class GlobalOccupancyPolicy : OccupancyStrategy {
    override fun tierFor(occupancy: Occupancy): OccupancyTier = OccupancyTier.of(occupancy.rate)

    override fun admit(occupancy: Occupancy): DomainResult<OccupancyTier> {
        val tier = tierFor(occupancy)
        return if (tier.acceptsEntry) tier.asSuccess() else DomainError.GarageFull.asFailure()
    }
}
