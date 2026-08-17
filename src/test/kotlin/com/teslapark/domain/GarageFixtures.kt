package com.teslapark.domain

import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.Garage
import com.teslapark.domain.model.GarageConfiguration
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.model.Spot
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

object GarageFixtures {
    val SECTOR_A: Sector =
        Sector(
            code = SectorCode("A"),
            basePrice = Money.of("40.50"),
            maxCapacity = 10,
            openHour = LocalTime.of(0, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(1440),
        )

    val SECTOR_B: Sector =
        Sector(
            code = SectorCode("B"),
            basePrice = Money.of("4.10"),
            maxCapacity = 20,
            openHour = LocalTime.of(8, 0),
            closeHour = LocalTime.of(23, 59),
            durationLimit = Duration.ofMinutes(60),
        )

    val FIRST_SPOT_COORDINATES: Coordinates = Coordinates.of("-23.561684", "-46.655981")

    fun sectorWith(
        code: String,
        basePrice: String,
        maxCapacity: Int = 10,
    ): Sector = SECTOR_A.copy(code = SectorCode(code), basePrice = Money.of(basePrice), maxCapacity = maxCapacity)

    fun spotAt(
        externalId: Long,
        latitude: String,
        longitude: String,
        sector: SectorCode = SECTOR_A.code,
    ): Spot = Spot(externalId, sector, Coordinates.of(latitude, longitude))

    fun garage(sectors: List<Sector> = listOf(SECTOR_A, SECTOR_B)): Garage =
        Garage(
            name = "sp-01",
            timezone = Garage.DEFAULT_TIMEZONE,
            currency = CurrencyCode.BRL,
            sectors = sectors,
        )

    fun configuration(spots: Int = 30): GarageConfiguration =
        GarageConfiguration(
            garage = garage(),
            spots =
                (1..spots).map { index ->
                    Spot(
                        externalId = index.toLong(),
                        sectorCode = if (index <= SECTOR_A.maxCapacity) SECTOR_A.code else SECTOR_B.code,
                        coordinates = Coordinates.of(BigDecimal("-23.5616$index"), BigDecimal("-46.6559$index")),
                    )
                },
        )

    fun enteredSession(
        plate: String,
        entryTime: Instant,
        occupancyRate: String = "0.4667",
        multiplier: String = "1.000",
    ): ParkingSession =
        ParkingSession.enter(
            licensePlate = LicensePlate(plate),
            entryTime = entryTime,
            occupancyRateAtEntry = BigDecimal(occupancyRate),
            priceMultiplier = BigDecimal(multiplier),
        )
}
