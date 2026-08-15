package com.teslapark.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class CurrencyCode(
    val code: String,
) {
    override fun toString(): String = code

    companion object {
        val BRL = CurrencyCode("BRL")
    }
}

data class Money(
    val amount: BigDecimal,
    val currency: CurrencyCode,
) : Comparable<Money> {
    val isZero: Boolean get() = amount.signum() == 0

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    operator fun times(factor: Int): Money = of(amount.multiply(BigDecimal(factor)), currency)

    operator fun times(factor: BigDecimal): Money = of(amount.multiply(factor), currency)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    override fun toString(): String = "${amount.toPlainString()} $currency"

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "cannot combine $currency with ${other.currency}" }
    }

    companion object {
        const val SCALE = 2

        fun zero(currency: CurrencyCode = CurrencyCode.BRL): Money = of(BigDecimal.ZERO, currency)

        fun of(
            amount: BigDecimal,
            currency: CurrencyCode = CurrencyCode.BRL,
        ): Money = Money(amount.setScale(SCALE, RoundingMode.CEILING), currency)

        fun of(
            amount: String,
            currency: CurrencyCode = CurrencyCode.BRL,
        ): Money = of(BigDecimal(amount), currency)
    }
}
