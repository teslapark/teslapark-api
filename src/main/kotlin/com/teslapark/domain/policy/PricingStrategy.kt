package com.teslapark.domain.policy

import com.teslapark.domain.model.Money
import java.math.BigDecimal
import java.time.Duration

interface PricingStrategy {
    fun charge(
        stay: Duration,
        basePrice: Money,
        multiplier: BigDecimal,
    ): SessionCharge

    fun charge(
        stay: Duration,
        basePrice: Money,
        tier: OccupancyTier,
    ): SessionCharge = charge(stay, basePrice, tier.multiplier)

    fun chargeableHours(stay: Duration): Int

    fun isWithinFreeWindow(stay: Duration): Boolean
}
