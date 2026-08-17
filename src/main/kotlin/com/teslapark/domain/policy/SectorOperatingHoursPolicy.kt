package com.teslapark.domain.policy

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.Sector
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Singleton
class SectorOperatingHoursPolicy : OperatingHoursStrategy {
    override fun isOpen(
        sector: Sector,
        at: Instant,
        zone: ZoneId,
    ): Boolean = sector.isOpenAt(localTimeOf(at, zone))

    override fun admitOperation(
        sector: Sector,
        at: Instant,
        zone: ZoneId,
    ): DomainResult<Sector> =
        if (isOpen(sector, at, zone)) {
            sector.asSuccess()
        } else {
            DomainError.SectorClosed(sector.code.value).asFailure()
        }

    override fun admitStay(
        sector: Sector,
        stay: Duration,
    ): DomainResult<Duration> =
        if (sector.exceedsDurationLimit(stay)) {
            DomainError
                .DurationLimitExceeded(
                    code = sector.code.value,
                    limitMinutes = sector.durationLimitMinutes,
                    stayMinutes = stay.toMinutes(),
                ).asFailure()
        } else {
            stay.asSuccess()
        }

    private fun localTimeOf(
        at: Instant,
        zone: ZoneId,
    ): LocalTime = at.atZone(zone).toLocalTime()
}
