package com.teslapark.infrastructure.metrics

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Controller("/metrics")
@ExecuteOn(TaskExecutors.BLOCKING)
class PrometheusController(
    private val registry: PrometheusMeterRegistry,
) {
    @Get
    @Produces(MediaType.TEXT_PLAIN)
    fun scrape(): String = registry.scrape()
}
