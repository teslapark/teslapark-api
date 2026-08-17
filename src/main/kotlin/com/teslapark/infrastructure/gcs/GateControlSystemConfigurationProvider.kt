package com.teslapark.infrastructure.gcs

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.GarageConfiguration
import com.teslapark.domain.port.GarageConfigurationProvider
import com.teslapark.infrastructure.config.GarageConfigurationProperties
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.retry.exception.CircuitOpenException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class GateControlSystemConfigurationProvider(
    private val client: GateControlSystemClient,
    private val garage: GarageConfigurationProperties,
) : GarageConfigurationProvider {
    private val logger = LoggerFactory.getLogger(GateControlSystemConfigurationProvider::class.java)

    override fun fetchConfiguration(): DomainResult<GarageConfiguration> =
        try {
            client.fetchGarage().toDomain(garage.name, garage.zone, garage.currencyCode).asSuccess()
        } catch (unreachable: HttpClientException) {
            logger.warn("gate control system is unreachable: {}", unreachable.message)
            DomainError.GarageConfigurationUnavailable.asFailure()
        } catch (circuitOpen: CircuitOpenException) {
            logger.warn("gate control system circuit is open: {}", circuitOpen.message)
            DomainError.GarageConfigurationUnavailable.asFailure()
        }
}
