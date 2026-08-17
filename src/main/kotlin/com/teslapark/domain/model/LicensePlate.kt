package com.teslapark.domain.model

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess

@JvmInline
value class LicensePlate private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 16

        private val ALLOWED = Regex("^[A-Z0-9-]{1,$MAX_LENGTH}$")

        operator fun invoke(raw: String): LicensePlate {
            val normalized = normalize(raw)
            require(ALLOWED.matches(normalized)) { "license plate must be 1 to $MAX_LENGTH characters of A-Z, 0-9 or -" }
            return LicensePlate(normalized)
        }

        fun parse(raw: String?): DomainResult<LicensePlate> {
            val normalized = normalize(raw.orEmpty())
            return if (ALLOWED.matches(normalized)) {
                LicensePlate(normalized).asSuccess()
            } else {
                DomainError.InvalidLicensePlate(raw.orEmpty().take(MAX_LENGTH)).asFailure()
            }
        }

        private fun normalize(raw: String): String = raw.trim().uppercase()
    }
}
