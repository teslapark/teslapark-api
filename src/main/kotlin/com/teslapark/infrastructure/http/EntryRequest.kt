package com.teslapark.infrastructure.http

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.event.GateEvent

@JsonIgnoreProperties(ignoreUnknown = true)
class EntryRequest
    @JsonCreator
    constructor(
        @JsonProperty("license_plate") @JsonAlias("licensePlate") val licensePlate: String? = null,
        @JsonProperty("entry_time") @JsonAlias("entryTime") val entryTime: String? = null,
    ) : WebhookRequest() {
        override fun toGateEvent(): DomainResult<GateEvent> {
            val plate = plateOf(licensePlate)
            if (plate is DomainResult.Failure) return plate

            return when (val time = instantOf("entry_time", entryTime)) {
                is DomainResult.Failure -> time
                is DomainResult.Success ->
                    GateEvent.EntryEvent((plate as DomainResult.Success).value, time.value).asSuccess()
            }
        }
    }
