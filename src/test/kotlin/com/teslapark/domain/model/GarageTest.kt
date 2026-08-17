package com.teslapark.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.LocalTime

class GarageTest {
    private val sectorA =
        Sector(
            code = SectorCode("A"),
            basePrice = Money.of("40.50"),
            maxCapacity = 10,
            openHour = LocalTime.of(0, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(1440),
        )
    private val sectorB =
        Sector(
            code = SectorCode("B"),
            basePrice = Money.of("4.10"),
            maxCapacity = 20,
            openHour = LocalTime.of(8, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(60),
        )
    private val garage =
        Garage(
            name = "sp-01",
            timezone = Garage.DEFAULT_TIMEZONE,
            currency = CurrencyCode.BRL,
            sectors = listOf(sectorA, sectorB),
        )

    @Test
    fun `total capacity is the sum of the sectors`() {
        garage.totalCapacity shouldBe 30
        garage.sectorBy(SectorCode("B")) shouldBe sectorB
        garage.sectorBy(SectorCode("Z")) shouldBe null
    }

    @ParameterizedTest
    @CsvSource("07:59, false", "08:00, true", "23:59, true", "00:00, false")
    fun `sector operating window is inclusive on both ends`(
        at: String,
        open: Boolean,
    ) {
        sectorB.isOpenAt(LocalTime.parse(at)) shouldBe open
    }

    @Test
    fun `sector detects a stay beyond its duration limit`() {
        sectorB.exceedsDurationLimit(Duration.ofMinutes(60)) shouldBe false
        sectorB.exceedsDurationLimit(Duration.ofMinutes(61)) shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "0, 30, 0.0000, false",
        "14, 30, 0.4666, false",
        "29, 30, 0.9666, false",
        "30, 30, 1.0000, true",
    )
    fun `occupancy derives rate and fullness from the garage total`(
        occupied: Int,
        capacity: Int,
        rate: String,
        full: Boolean,
    ) {
        val occupancy = Occupancy(occupied, capacity)

        occupancy.rate.toPlainString() shouldBe rate
        occupancy.isFull shouldBe full
        occupancy.availableSpots shouldBe capacity - occupied
    }

    @Test
    fun `an empty garage is never full`() {
        Occupancy.empty(totalCapacity = 0).isFull shouldBe false
        garage.occupancyOf(15).rate.toPlainString() shouldBe "0.5000"
    }

    @Test
    fun `spot occupation is immutable`() {
        val free = Spot(1, SectorCode("A"), Coordinates.of("-23.561684", "-46.655981"))
        val taken = free.occupy()

        free.occupied shouldBe false
        taken.occupied shouldBe true
        taken.release().occupied shouldBe false
    }
}
