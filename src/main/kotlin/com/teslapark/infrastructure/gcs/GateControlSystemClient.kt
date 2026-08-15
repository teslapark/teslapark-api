package com.teslapark.infrastructure.gcs

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.CircuitBreaker

@Client(id = GateControlSystemClient.SERVICE_ID)
@CircuitBreaker(
    attempts = "\${teslapark.gcs.retry.attempts:3}",
    delay = "\${teslapark.gcs.retry.delay:200ms}",
    multiplier = "\${teslapark.gcs.retry.multiplier:2.0}",
    reset = "\${teslapark.gcs.circuit-breaker.reset:30s}",
)
interface GateControlSystemClient {
    @Get("/garage")
    fun fetchGarage(): GarageResponse

    companion object {
        const val SERVICE_ID = "gate-control-system"
    }
}
