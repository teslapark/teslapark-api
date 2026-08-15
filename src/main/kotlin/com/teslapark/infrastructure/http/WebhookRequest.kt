package com.teslapark.infrastructure.http

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.LicensePlate
import java.math.BigDecimal
import java.time.Instant

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "event_type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = EntryRequest::class, name = "ENTRY"),
    JsonSubTypes.Type(value = ParkedRequest::class, name = "PARKED"),
    JsonSubTypes.Type(value = ExitRequest::class, name = "EXIT"),
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class WebhookRequest {
    abstract fun toGateEvent(): DomainResult<GateEvent>

    protected fun plateOf(raw: String?): DomainResult<LicensePlate> =
        raw?.let { LicensePlate.parse(it) } ?: DomainError.InvalidLicensePlate("").asFailure()

    protected fun instantOf(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

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

            val time = instantOf(entryTime) ?: return DomainError.MissingEventField("entry_time").asFailure()
            return GateEvent.EntryEvent((plate as DomainResult.Success).value, time).asSuccess()
        }
    }

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
