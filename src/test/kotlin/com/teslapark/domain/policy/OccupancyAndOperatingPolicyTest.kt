package com.teslapark.domain.policy

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Occupancy
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class OccupancyAndOperatingPolicyTest {
    private val occupancyPolicy = GlobalOccupancyPolicy()
    private val operatingHours = SectorOperatingHoursPolicy()

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private val sectorB =
        Sector(
            code = SectorCode("B"),
            basePrice = Money.of("4.10"),
            maxCapacity = 20,
            openHour = LocalTime.of(8, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(60),
        )

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "0.0000, LOW",
        "0.2499, LOW",
        "0.2500, LOW",
        "0.2501, NORMAL",
        "0.4999, NORMAL",
        "0.5000, NORMAL",
        "0.5001, HIGH",
        "0.7499, HIGH",
        "0.7500, HIGH",
        "0.7501, PEAK",
        "0.9999, PEAK",
        "1.0000, FULL",
    )
    fun `every tier boundary maps to the specified tier`(
        rate: String,
        expected: OccupancyTier,
    ) {
        OccupancyTier.of(BigDecimal(rate)) shouldBe expected
    }

    @ParameterizedTest
    @CsvSource("LOW, 0.900", "NORMAL, 1.000", "HIGH, 1.100", "PEAK, 1.250")
    fun `multipliers match the published price table`(
        tier: OccupancyTier,
        multiplier: String,
    ) {
        tier.multiplier shouldBe BigDecimal(multiplier)
        tier.acceptsEntry shouldBe true
    }

    @Test
    fun `a full garage refuses entry and the first exit reopens it`() {
        val full = Occupancy(occupiedSpots = 30, totalCapacity = 30)

        occupancyPolicy.admitsEntry(full) shouldBe false
        occupancyPolicy.admit(full).errorOrNull() shouldBe DomainError.GarageFull

        val afterOneExit = Occupancy(occupiedSpots = 29, totalCapacity = 30)
        occupancyPolicy.admitsEntry(afterOneExit) shouldBe true
        occupancyPolicy.admit(afterOneExit).valueOrNull() shouldBe OccupancyTier.PEAK
    }

    @Test
    fun `occupancy is evaluated globally and never per sector`() {
        occupancyPolicy.tierFor(Occupancy(occupiedSpots = 15, totalCapacity = 30)) shouldBe OccupancyTier.NORMAL
        occupancyPolicy.tierFor(Occupancy(occupiedSpots = 16, totalCapacity = 30)) shouldBe OccupancyTier.HIGH
        occupancyPolicy.tierFor(Occupancy.empty(totalCapacity = 30)) shouldBe OccupancyTier.LOW
    }

    @ParameterizedTest
    @CsvSource(
        "2026-08-15T10:59:59Z, false",
        "2026-08-15T11:00:00Z, true",
        "2026-08-16T02:58:00Z, true",
        "2026-08-16T03:00:00Z, false",
    )
    fun `sector window is evaluated in the garage local time`(
        instant: String,
        open: Boolean,
    ) {
        operatingHours.isOpen(sectorB, Instant.parse(instant), saoPaulo) shouldBe open
    }

    @Test
    fun `a closed sector rejects the operation`() {
        val beforeOpening = Instant.parse("2026-08-15T10:00:00Z")

        operatingHours
            .admitOperation(sectorB, beforeOpening, saoPaulo)
            .errorOrNull() shouldBe DomainError.SectorClosed("B")

        operatingHours
            .admitOperation(sectorB, Instant.parse("2026-08-15T12:00:00Z"), saoPaulo)
            .valueOrNull() shouldBe sectorB
    }

    @Test
    fun `stay beyond the sector limit is rejected at the exact minute`() {
        operatingHours.admitStay(sectorB, Duration.ofMinutes(60)).valueOrNull() shouldBe Duration.ofMinutes(60)

        val error = operatingHours.admitStay(sectorB, Duration.ofMinutes(61)).errorOrNull()

        error.shouldBeInstanceOf<DomainError.DurationLimitExceeded>()
        error.limitMinutes shouldBe 60L
        error.stayMinutes shouldBe 61L
    }
}
