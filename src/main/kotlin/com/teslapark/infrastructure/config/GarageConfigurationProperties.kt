package com.teslapark.infrastructure.config

import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.Garage
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.time.ZoneId

@Singleton
class GarageConfigurationProperties(
    @Value("\${teslapark.garage.name:sp-01}") val name: String,
    @Value("\${teslapark.garage.timezone:" + Garage.DEFAULT_TIMEZONE_ID + "}") val timezone: String,
    @Value("\${teslapark.garage.currency:" + CurrencyCode.BRL_CODE + "}") val currency: String,
) {
    val zone: ZoneId = ZoneId.of(timezone)

    val currencyCode: CurrencyCode = CurrencyCode(currency)

    init {
        require(zone == Garage.DEFAULT_TIMEZONE || timezone.isNotBlank()) { "operating timezone must be resolvable" }
    }
}
