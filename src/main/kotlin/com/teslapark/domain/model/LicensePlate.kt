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
        operator fun invoke(raw: String): LicensePlate {
            val normalized = normalize(raw)
            require(normalized.isNotEmpty()) { "license plate must not be blank" }
            return LicensePlate(normalized)
        }

        fun parse(raw: String): DomainResult<LicensePlate> {
            val normalized = normalize(raw)
            return if (normalized.isEmpty()) {
                DomainError.InvalidLicensePlate(raw).asFailure()
            } else {
                LicensePlate(normalized).asSuccess()
            }
        }

        private fun normalize(raw: String): String = raw.trim().uppercase()
    }
}
