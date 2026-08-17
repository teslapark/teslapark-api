package com.teslapark.domain.policy

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.Sector
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

interface OperatingHoursStrategy {
    fun isOpen(
        sector: Sector,
        at: Instant,
        zone: ZoneId,
    ): Boolean

    fun admitOperation(
        sector: Sector,
        at: Instant,
        zone: ZoneId,
    ): DomainResult<Sector>

    fun admitStay(
        sector: Sector,
        stay: Duration,
    ): DomainResult<Duration>
}
