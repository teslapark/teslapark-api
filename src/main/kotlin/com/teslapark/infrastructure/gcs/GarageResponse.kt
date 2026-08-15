package com.teslapark.infrastructure.gcs

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class GarageResponse
    @JsonCreator
    constructor(
        @JsonProperty("garage") val garage: List<SectorPayload> = emptyList(),
        @JsonProperty("spots") val spots: List<SpotPayload> = emptyList(),
    )

@JsonIgnoreProperties(ignoreUnknown = true)
class SectorPayload
    @JsonCreator
    constructor(
        @JsonProperty("sector") val sector: String,
        @JsonProperty("base_price") @JsonAlias("basePrice") val basePrice: BigDecimal,
        @JsonProperty("max_capacity") @JsonAlias("maxCapacity") val maxCapacity: Int,
        @JsonProperty("open_hour") @JsonAlias("openHour") val openHour: String,
        @JsonProperty("close_hour") @JsonAlias("closeHour") val closeHour: String,
        @JsonProperty("duration_limit_minutes") @JsonAlias("durationLimitMinutes") val durationLimitMinutes: Long,
    )

@JsonIgnoreProperties(ignoreUnknown = true)
class SpotPayload
    @JsonCreator
    constructor(
        @JsonProperty("id") @JsonAlias("external_id", "externalId") val id: Long,
        @JsonProperty("sector") val sector: String,
        @JsonProperty("lat") @JsonAlias("latitude") val lat: BigDecimal,
        @JsonProperty("lng") @JsonAlias("longitude") val lng: BigDecimal,
        @JsonProperty("occupied") val occupied: Boolean = false,
    )
