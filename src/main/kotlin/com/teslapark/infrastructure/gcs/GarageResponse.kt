package com.teslapark.infrastructure.gcs

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class GarageResponse
    @JsonCreator
    constructor(
        @JsonProperty("garage") val garage: List<SectorPayload> = emptyList(),
        @JsonProperty("spots") val spots: List<SpotPayload> = emptyList(),
    )
