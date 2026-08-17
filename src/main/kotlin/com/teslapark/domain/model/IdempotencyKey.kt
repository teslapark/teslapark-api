package com.teslapark.domain.model

@JvmInline
value class IdempotencyKey private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val LENGTH = 64

        operator fun invoke(raw: String): IdempotencyKey {
            require(raw.length == LENGTH) { "idempotency key must be a $LENGTH character digest" }
            return IdempotencyKey(raw)
        }
    }
}
