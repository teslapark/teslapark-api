package com.teslapark.domain.model

@JvmInline
value class SectorCode private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        operator fun invoke(raw: String): SectorCode {
            val normalized = raw.trim().uppercase()
            require(normalized.isNotEmpty()) { "sector code must not be blank" }
            return SectorCode(normalized)
        }
    }
}
