package com.teslapark.infrastructure.gcs

import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.Garage
import com.teslapark.domain.model.GarageConfiguration
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.model.Spot
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId

fun GarageResponse.toDomain(
    garageName: String,
    timezone: ZoneId,
    currency: CurrencyCode,
): GarageConfiguration =
    GarageConfiguration(
        garage =
            Garage(
                name = garageName,
                timezone = timezone,
                currency = currency,
                sectors = garage.map { it.toDomain(currency) },
            ),
        spots = spots.map { it.toDomain() },
    )

fun SectorPayload.toDomain(currency: CurrencyCode): Sector =
    Sector(
        code = SectorCode(sector),
        basePrice = Money.of(basePrice, currency),
        maxCapacity = maxCapacity,
        openHour = LocalTime.parse(openHour),
        closeHour = LocalTime.parse(closeHour),
        durationLimit = Duration.ofMinutes(durationLimitMinutes),
    )

fun SpotPayload.toDomain(): Spot =
    Spot(
        externalId = id,
        sectorCode = SectorCode(sector),
        coordinates = Coordinates.of(lat, lng),
        occupied = occupied,
    )
