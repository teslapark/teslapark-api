package com.teslapark.infrastructure.http.problem

import com.teslapark.domain.error.DomainError
import io.micronaut.http.HttpStatus

object ProblemCatalogue {
    const val BASE_TYPE = "https://api.teslapark.local/errors"

    val UNAUTHORIZED = ProblemKind(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized")
    val FORBIDDEN = ProblemKind(HttpStatus.FORBIDDEN, "forbidden", "Forbidden")
    val RATE_LIMITED = ProblemKind(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", "Too Many Requests")
    val VALIDATION = ProblemKind(HttpStatus.BAD_REQUEST, "validation", "Validation failed")
    val INTERNAL = ProblemKind(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal Server Error")
    val NOT_FOUND = ProblemKind(HttpStatus.NOT_FOUND, "not-found", "Not Found")
    val GARAGE_NOT_CONFIGURED =
        ProblemKind(HttpStatus.SERVICE_UNAVAILABLE, "garage-not-configured", "Garage configuration unavailable")

    fun typeOf(slug: String): String = "$BASE_TYPE/$slug"

    fun kindOf(error: DomainError): ProblemKind =
        when (error) {
            is DomainError.GarageFull -> ProblemKind(HttpStatus.CONFLICT, "garage-full", "Garage is full")

            is DomainError.SessionAlreadyOpen ->
                ProblemKind(HttpStatus.CONFLICT, "session-already-open", "Session already open")

            is DomainError.SessionAlreadyClosed, is DomainError.InvalidSessionTransition ->
                ProblemKind(HttpStatus.CONFLICT, "invalid-session-transition", "Invalid session transition")

            is DomainError.SpotAlreadyOccupied ->
                ProblemKind(HttpStatus.CONFLICT, "spot-already-occupied", "Spot already occupied")

            is DomainError.ExitTimeBeforeEntryTime ->
                ProblemKind(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-exit-time", "Invalid exit time")

            is DomainError.ParkedTimeBeforeEntryTime ->
                ProblemKind(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-parked-time", "Invalid parked time")

            is DomainError.SectorNotFound -> ProblemKind(HttpStatus.NOT_FOUND, "sector-not-found", "Sector not found")
            is DomainError.SpotNotFound -> ProblemKind(HttpStatus.NOT_FOUND, "spot-not-found", "Spot not found")
            is DomainError.SessionNotFound -> ProblemKind(HttpStatus.NOT_FOUND, "session-not-found", "Session not found")
            is DomainError.SpotNotHeld -> ProblemKind(HttpStatus.NOT_FOUND, "spot-not-held", "Spot not held")

            is DomainError.GarageConfigurationUnavailable -> GARAGE_NOT_CONFIGURED

            is DomainError.InvalidLicensePlate,
            is DomainError.InvalidCoordinates,
            is DomainError.MissingEventField,
            is DomainError.MalformedEventPayload,
            is DomainError.CurrencyMismatch,
            -> VALIDATION

            is DomainError.DuplicateWebhookEvent ->
                ProblemKind(HttpStatus.CONFLICT, "duplicate-event", "Duplicate event")

            is DomainError.SectorClosed -> ProblemKind(HttpStatus.CONFLICT, "sector-closed", "Sector closed")

            is DomainError.DurationLimitExceeded ->
                ProblemKind(HttpStatus.CONFLICT, "duration-limit-exceeded", "Duration limit exceeded")
        }

    fun fieldErrorsOf(error: DomainError): List<FieldError> =
        when (error) {
            is DomainError.InvalidLicensePlate ->
                listOf(FieldError("license_plate", "must be 1 to 16 characters of A-Z, 0-9 or -"))

            is DomainError.InvalidCoordinates -> listOf(FieldError("lat/lng", "must be a valid earth coordinate"))
            is DomainError.MissingEventField -> listOf(FieldError(error.field, "is required"))
            else -> emptyList()
        }
}
