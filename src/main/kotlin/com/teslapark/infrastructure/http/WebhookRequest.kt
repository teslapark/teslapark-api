package com.teslapark.infrastructure.http

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.LicensePlate
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
