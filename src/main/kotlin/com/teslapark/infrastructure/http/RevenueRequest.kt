package com.teslapark.infrastructure.http

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class RevenueRequest
    @JsonCreator
    constructor(
        @JsonProperty("date") val date: String? = null,
        @JsonProperty("sector") val sector: String? = null,
    )
