package com.teslapark.infrastructure.gcs

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class SpotPayload
    @JsonCreator
    constructor(
        @JsonProperty("id") @JsonAlias("external_id", "externalId") val id: Long,
        @JsonProperty("sector") val sector: String,
        @JsonProperty("lat") @JsonAlias("latitude") val lat: BigDecimal,
        @JsonProperty("lng") @JsonAlias("longitude") val lng: BigDecimal,
        @JsonProperty("occupied") val occupied: Boolean? = null,
    )
