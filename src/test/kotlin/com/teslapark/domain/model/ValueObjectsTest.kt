package com.teslapark.domain.model

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class ValueObjectsTest {
    @Test
    fun `license plate normalizes case so equality never depends on it`() {
        LicensePlate("zul0001") shouldBe LicensePlate("ZUL0001")
        LicensePlate("  zul0001  ").value shouldBe "ZUL0001"
    }

    @Test
    fun `license plate rejects a blank value`() {
        assertThrows<IllegalArgumentException> { LicensePlate("   ") }
        LicensePlate.parse("   ").errorOrNull().shouldBeInstanceOf<DomainError.InvalidLicensePlate>()
        LicensePlate.parse("zul0001").valueOrNull() shouldBe LicensePlate("ZUL0001")
    }

    @ParameterizedTest
    @CsvSource(
        "-23.561684, -23.5616840",
        "-46.655981, -46.65598100",
        "10, 10.000000",
    )
    fun `coordinates with different representations are equivalent`(
        written: String,
        padded: String,
    ) {
        Coordinates.of(written, written) shouldBe Coordinates.of(padded, padded)
    }

    @Test
    fun `coordinates outside the earth are rejected`() {
        Coordinates.parse(BigDecimal("-91"), BigDecimal("0")).shouldBeInstanceOf<DomainResult.Failure>()
        Coordinates.parse(BigDecimal("0"), BigDecimal("181")).shouldBeInstanceOf<DomainResult.Failure>()
        Coordinates
            .parse(BigDecimal("-23.561684"), BigDecimal("-46.655981"))
            .shouldBeInstanceOf<DomainResult.Success<Coordinates>>()
    }

    @Test
    fun `money keeps precision across chained sum and multiplication`() {
        val threeHoursOfSectorB = Money.of("4.10") * 3 * BigDecimal("1.10")

        threeHoursOfSectorB shouldBe Money.of("13.53")
        threeHoursOfSectorB.amount.toPlainString() shouldBe "13.53"
    }

    @Test
    fun `money sums a long tail of cents without drift`() {
        val cents = List(100) { Money.of("0.01") }

        cents.reduce(Money::plus) shouldBe Money.of("1.00")
    }

    @Test
    fun `money compares by value and not by scale`() {
        Money.of(BigDecimal("40.5")) shouldBe Money.of("40.50")
        Money.of("40.50") shouldNotBe Money.of("40.51")
        Money.zero().isZero shouldBe true
    }

    @Test
    fun `money refuses to mix currencies`() {
        assertThrows<IllegalArgumentException> { Money.of("1.00", CurrencyCode.BRL) + Money.of("1.00", CurrencyCode("USD")) }
    }

    @Test
    fun `sector code normalizes and rejects blank`() {
        SectorCode("a") shouldBe SectorCode("A")
        assertThrows<IllegalArgumentException> { SectorCode(" ") }
    }
}
