package com.teslapark.domain.model

@JvmInline
value class CurrencyCode(
    val code: String,
) {
    override fun toString(): String = code

    companion object {
        const val BRL_CODE = "BRL"

        val BRL = CurrencyCode(BRL_CODE)
    }
}
