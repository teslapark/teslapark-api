package com.teslapark.domain.policy

import com.teslapark.domain.model.Money
import java.math.BigDecimal
import java.time.Duration

data class SessionCharge(
    val chargeableHours: Int,
    val amount: Money,
) {
    val isWithinFreeWindow: Boolean get() = chargeableHours == 0
}

object PricingPolicy {
    const val FREE_WINDOW_MINUTES = 30L

    private const val MINUTES_PER_HOUR = 60L

    val FREE_WINDOW: Duration = Duration.ofMinutes(FREE_WINDOW_MINUTES)

    fun isWithinFreeWindow(stay: Duration): Boolean = stay <= FREE_WINDOW

    fun chargeableHours(stay: Duration): Int =
        if (isWithinFreeWindow(stay)) {
            0
        } else {
            Math.ceilDiv(stay.toMinutes(), MINUTES_PER_HOUR).toInt()
        }

    fun charge(
        stay: Duration,
        basePrice: Money,
        multiplier: BigDecimal,
    ): SessionCharge {
        val hours = chargeableHours(stay)
        if (hours == 0) return SessionCharge(0, Money.zero(basePrice.currency))

        val amount =
            basePrice.amount
                .multiply(BigDecimal(hours))
                .multiply(multiplier)

        return SessionCharge(hours, Money.of(amount, basePrice.currency))
    }

    fun charge(
        stay: Duration,
        basePrice: Money,
        tier: OccupancyTier,
    ): SessionCharge = charge(stay, basePrice, tier.multiplier)
}
