package com.teslapark.domain.policy

import com.teslapark.domain.model.Money
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.Duration

class PricingPolicyTest {
    private val pricingPolicy = TieredPricingPolicy()

    @ParameterizedTest(name = "{4}: {0}min x {1} x {2} = {3}")
    @CsvSource(
        "29,  40.50, 1.000,   0.00, within the free window",
        "30,  40.50, 1.000,   0.00, exact free window boundary",
        "31,  40.50, 1.000,  40.50, first charged minute",
        "60,  40.50, 1.000,  40.50, exact hour",
        "61,  40.50, 1.000,  81.00, rounds the hour up",
        "130, 40.50, 1.000, 121.50, long stay",
        "130,  4.10, 1.100,  13.53, decimal precision",
    )
    fun `charges the stay according to the tariff table`(
        minutes: Long,
        basePrice: String,
        multiplier: String,
        expected: String,
        case: String,
    ) {
        val charge = pricingPolicy.charge(Duration.ofMinutes(minutes), Money.of(basePrice), BigDecimal(multiplier))

        withClue(case) {
            charge.amount shouldBe Money.of(expected)
            charge.amount.amount.toPlainString() shouldBe expected
        }
    }

    @ParameterizedTest
    @CsvSource("0, 0", "29, 0", "30, 0", "31, 1", "60, 1", "61, 2", "120, 2", "121, 3", "130, 3")
    fun `chargeable hours round up past the free window`(
        minutes: Long,
        hours: Int,
    ) {
        pricingPolicy.chargeableHours(Duration.ofMinutes(minutes)) shouldBe hours
    }

    @Test
    fun `a free session reports zero hours and zero amount`() {
        val charge = pricingPolicy.charge(Duration.ofMinutes(22), Money.of("40.50"), OccupancyTier.PEAK)

        charge.isWithinFreeWindow shouldBe true
        charge.chargeableHours shouldBe 0
        charge.amount shouldBe Money.zero()
    }

    @Test
    fun `the free window boundary is inclusive`() {
        pricingPolicy.isWithinFreeWindow(TieredPricingPolicy.FREE_WINDOW) shouldBe true
        pricingPolicy.isWithinFreeWindow(TieredPricingPolicy.FREE_WINDOW.plusSeconds(1)) shouldBe false
    }

    @ParameterizedTest
    @CsvSource("LOW, 36.45", "NORMAL, 40.50", "HIGH, 44.55", "PEAK, 50.63")
    fun `each tier applies its multiplier to a single hour`(
        tier: OccupancyTier,
        expected: String,
    ) {
        pricingPolicy.charge(Duration.ofMinutes(60), Money.of("40.50"), tier).amount shouldBe Money.of(expected)
    }

    @Test
    fun `rounding never favours the operator by more than a cent`() {
        val charge = pricingPolicy.charge(Duration.ofMinutes(60), Money.of("40.50"), BigDecimal("1.250"))

        charge.amount.amount.toPlainString() shouldBe "50.63"
    }
}
