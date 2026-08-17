package com.teslapark.infrastructure.http

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.Coordinates
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class ParkedRequest
    @JsonCreator
    constructor(
        @JsonProperty("license_plate") @JsonAlias("licensePlate") val licensePlate: String? = null,
        @JsonProperty("lat") @JsonAlias("latitude") val lat: BigDecimal? = null,
        @JsonProperty("lng") @JsonAlias("longitude") val lng: BigDecimal? = null,
    ) : WebhookRequest() {
        override fun toGateEvent(): DomainResult<GateEvent> {
            val plate = plateOf(licensePlate)
            if (plate is DomainResult.Failure) return plate

            if (lat == null || lng == null) return DomainError.MissingEventField("lat/lng").asFailure()

            return when (val coordinates = Coordinates.parse(lat, lng)) {
                is DomainResult.Failure -> coordinates
                is DomainResult.Success ->
                    GateEvent.ParkedEvent((plate as DomainResult.Success).value, coordinates.value).asSuccess()
            }
        }
    }
