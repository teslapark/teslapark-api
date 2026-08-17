package com.teslapark.domain.policy

import com.teslapark.domain.model.Money
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Duration

@Singleton
class TieredPricingPolicy : PricingStrategy {
    override fun charge(
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

    override fun chargeableHours(stay: Duration): Int =
        if (isWithinFreeWindow(stay)) {
            0
        } else {
            Math.ceilDiv(stay.toMinutes(), MINUTES_PER_HOUR).toInt()
        }

    override fun isWithinFreeWindow(stay: Duration): Boolean = stay <= FREE_WINDOW

    companion object {
        const val FREE_WINDOW_MINUTES = 30L

        private const val MINUTES_PER_HOUR = 60L

        val FREE_WINDOW: Duration = Duration.ofMinutes(FREE_WINDOW_MINUTES)
    }
}
