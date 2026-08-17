package com.teslapark.domain.policy

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.Occupancy

interface OccupancyStrategy {
    fun tierFor(occupancy: Occupancy): OccupancyTier

    fun admit(occupancy: Occupancy): DomainResult<OccupancyTier>

    fun admitsEntry(occupancy: Occupancy): Boolean = tierFor(occupancy).acceptsEntry
}
