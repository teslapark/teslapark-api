package com.teslapark.infrastructure.gcs

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class SectorPayload
    @JsonCreator
    constructor(
        @JsonProperty("sector") val sector: String,
        @JsonProperty("base_price") @JsonAlias("basePrice") val basePrice: BigDecimal,
        @JsonProperty("max_capacity") @JsonAlias("maxCapacity") val maxCapacity: Int,
        @JsonProperty("open_hour") @JsonAlias("openHour") val openHour: String? = null,
        @JsonProperty("close_hour") @JsonAlias("closeHour") val closeHour: String? = null,
        @JsonProperty("duration_limit_minutes")
        @JsonAlias("durationLimitMinutes")
        val durationLimitMinutes: Long? = null,
    ) {
        val operatingOpenHour: String get() = openHour ?: ALL_DAY_OPEN

        val operatingCloseHour: String get() = closeHour ?: ALL_DAY_CLOSE

        val operatingDurationLimitMinutes: Long get() = durationLimitMinutes ?: ALL_DAY_MINUTES

        companion object {
            const val ALL_DAY_OPEN = "00:00"
            const val ALL_DAY_CLOSE = "23:59"
            const val ALL_DAY_MINUTES = 1440L
        }
    }
