package com.teslapark.domain.error

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
