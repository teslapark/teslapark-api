package com.teslapark.domain.model

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import java.math.BigDecimal
import java.math.RoundingMode

data class Coordinates(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
) {
    override fun toString(): String = "${latitude.toPlainString()},${longitude.toPlainString()}"

    companion object {
        const val SCALE = 6

        private val MIN_LATITUDE = BigDecimal("-90")
        private val MAX_LATITUDE = BigDecimal("90")
        private val MIN_LONGITUDE = BigDecimal("-180")
        private val MAX_LONGITUDE = BigDecimal("180")

        fun of(
            latitude: BigDecimal,
            longitude: BigDecimal,
        ): Coordinates = Coordinates(normalize(latitude), normalize(longitude))

        fun of(
            latitude: String,
            longitude: String,
        ): Coordinates = of(BigDecimal(latitude), BigDecimal(longitude))

        fun parse(
            latitude: BigDecimal,
            longitude: BigDecimal,
        ): DomainResult<Coordinates> =
            if (isWithinBounds(latitude, MIN_LATITUDE, MAX_LATITUDE) &&
                isWithinBounds(longitude, MIN_LONGITUDE, MAX_LONGITUDE)
            ) {
                of(latitude, longitude).asSuccess()
            } else {
                DomainError.InvalidCoordinates(latitude.toPlainString(), longitude.toPlainString()).asFailure()
            }

        private fun normalize(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_UP)

        private fun isWithinBounds(
            value: BigDecimal,
            min: BigDecimal,
            max: BigDecimal,
        ): Boolean = value >= min && value <= max
    }
}
