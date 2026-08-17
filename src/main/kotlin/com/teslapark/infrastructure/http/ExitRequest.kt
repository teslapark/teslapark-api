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

@JsonIgnoreProperties(ignoreUnknown = true)
class ExitRequest
    @JsonCreator
    constructor(
        @JsonProperty("license_plate") @JsonAlias("licensePlate") val licensePlate: String? = null,
        @JsonProperty("exit_time") @JsonAlias("exitTime") val exitTime: String? = null,
    ) : WebhookRequest() {
        override fun toGateEvent(): DomainResult<GateEvent> {
            val plate = plateOf(licensePlate)
            if (plate is DomainResult.Failure) return plate

            val time = instantOf(exitTime) ?: return DomainError.MissingEventField("exit_time").asFailure()
            return GateEvent.ExitEvent((plate as DomainResult.Success).value, time).asSuccess()
        }
    }
