package com.teslapark.domain.model

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

data class ParkingSession(
    val licensePlate: LicensePlate,
    val status: SessionStatus,
    val entryTime: Instant,
    val occupancyRateAtEntry: BigDecimal,
    val priceMultiplier: BigDecimal,
    val id: Long? = null,
    val sectorCode: SectorCode? = null,
    val spotExternalId: Long? = null,
    val parkedTime: Instant? = null,
    val exitTime: Instant? = null,
    val basePriceApplied: Money? = null,
    val billedHours: Int? = null,
    val amountCharged: Money? = null,
) {
    val isClosed: Boolean get() = status.isClosed

    val stay: Duration? get() = exitTime?.let { Duration.between(entryTime, it) }

    fun park(
        spot: Spot,
        at: Instant,
    ): DomainResult<ParkingSession> {
        val rejection = rejectTransitionTo(SessionStatus.PARKED)
        if (rejection != null) return rejection
        if (at < entryTime) return DomainError.ParkedTimeBeforeEntryTime(entryTime, at).asFailure()

        return copy(
            status = SessionStatus.PARKED,
            sectorCode = spot.sectorCode,
            spotExternalId = spot.externalId,
            parkedTime = at,
        ).asSuccess()
    }

    fun exit(at: Instant): DomainResult<ParkingSession> {
        val rejection = rejectTransitionTo(SessionStatus.EXITED)
        if (rejection != null) return rejection
        if (at < entryTime) return DomainError.ExitTimeBeforeEntryTime(entryTime, at).asFailure()

        return copy(status = SessionStatus.EXITED, exitTime = at).asSuccess()
    }

    fun cancel(): DomainResult<ParkingSession> =
        rejectTransitionTo(SessionStatus.CANCELLED) ?: copy(status = SessionStatus.CANCELLED).asSuccess()

    fun withCharge(
        basePrice: Money,
        billedHours: Int,
        amount: Money,
    ): ParkingSession = copy(basePriceApplied = basePrice, billedHours = billedHours, amountCharged = amount)

    private fun rejectTransitionTo(next: SessionStatus): DomainResult<ParkingSession>? =
        when {
            isClosed -> DomainError.SessionAlreadyClosed.asFailure()
            !status.canTransitionTo(next) -> DomainError.InvalidSessionTransition(status.name, next.name).asFailure()
            else -> null
        }

    companion object {
        fun enter(
            licensePlate: LicensePlate,
            entryTime: Instant,
            occupancyRateAtEntry: BigDecimal,
            priceMultiplier: BigDecimal,
        ): ParkingSession =
            ParkingSession(
                licensePlate = licensePlate,
                status = SessionStatus.ENTERED,
                entryTime = entryTime,
                occupancyRateAtEntry = occupancyRateAtEntry,
                priceMultiplier = priceMultiplier,
            )
    }
}
