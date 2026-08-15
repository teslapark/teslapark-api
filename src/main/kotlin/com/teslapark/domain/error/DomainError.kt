package com.teslapark.domain.error

import java.time.Instant

sealed interface DomainError {
    data class InvalidLicensePlate(
        val raw: String,
    ) : DomainError

    data class InvalidCoordinates(
        val latitude: String,
        val longitude: String,
    ) : DomainError

    data class CurrencyMismatch(
        val expected: String,
        val actual: String,
    ) : DomainError

    data object SessionAlreadyClosed : DomainError

    data class InvalidSessionTransition(
        val from: String,
        val to: String,
    ) : DomainError

    data class ExitTimeBeforeEntryTime(
        val entryTime: Instant,
        val exitTime: Instant,
    ) : DomainError

    data class ParkedTimeBeforeEntryTime(
        val entryTime: Instant,
        val parkedTime: Instant,
    ) : DomainError

    data object GarageFull : DomainError

    data class SectorNotFound(
        val code: String,
    ) : DomainError

    data class SpotNotFound(
        val latitude: String,
        val longitude: String,
    ) : DomainError

    data class SessionNotFound(
        val licensePlate: String,
    ) : DomainError

    data object GarageConfigurationUnavailable : DomainError

    data class SectorClosed(
        val code: String,
    ) : DomainError

    data class DurationLimitExceeded(
        val code: String,
        val limitMinutes: Long,
        val stayMinutes: Long,
    ) : DomainError
}

sealed interface DomainResult<out T> {
    data class Success<T>(
        val value: T,
    ) : DomainResult<T>

    data class Failure(
        val error: DomainError,
    ) : DomainResult<Nothing>

    fun errorOrNull(): DomainError? =
        when (this) {
            is Success -> null
            is Failure -> error
        }

    fun valueOrNull(): T? =
        when (this) {
            is Success -> value
            is Failure -> null
        }
}

fun <T> T.asSuccess(): DomainResult<T> = DomainResult.Success(this)

fun DomainError.asFailure(): DomainResult<Nothing> = DomainResult.Failure(this)
