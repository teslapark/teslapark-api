package com.teslapark.infrastructure.gcs

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.GarageConfiguration
import com.teslapark.domain.port.GarageConfigurationProvider
import io.micronaut.context.annotation.Value
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.retry.exception.CircuitOpenException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.ZoneId

@Singleton
class GateControlSystemConfigurationProvider(
    private val client: GateControlSystemClient,
    @Value("\${teslapark.garage.name:sp-01}") private val garageName: String,
    @Value("\${teslapark.garage.timezone:America/Sao_Paulo}") timezone: String,
    @Value("\${teslapark.garage.currency:BRL}") currency: String,
) : GarageConfigurationProvider {
    private val logger = LoggerFactory.getLogger(GateControlSystemConfigurationProvider::class.java)
    private val zone: ZoneId = ZoneId.of(timezone)
    private val currencyCode = CurrencyCode(currency)

    override fun fetchConfiguration(): DomainResult<GarageConfiguration> =
        try {
            client.fetchGarage().toDomain(garageName, zone, currencyCode).asSuccess()
        } catch (unreachable: HttpClientException) {
            logger.warn("gate control system is unreachable: {}", unreachable.message)
            DomainError.GarageConfigurationUnavailable.asFailure()
        } catch (circuitOpen: CircuitOpenException) {
            logger.warn("gate control system circuit is open: {}", circuitOpen.message)
            DomainError.GarageConfigurationUnavailable.asFailure()
        }
}
