package com.teslapark.infrastructure.http

import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.GarageStateRepository
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter

@ServerFilter(patterns = ["/webhook", "/revenue", "/garage/status", "/plate-status", "/spot-status"])
class GarageConfigurationFilter(
    private val garageState: GarageStateRepository,
) {
    @RequestFilter
    fun rejectWhileConfigurationIsPending(request: HttpRequest<*>): HttpResponse<*>? {
        val status = runCatching { garageState.currentStatus() }.getOrDefault(GarageConfigurationStatus.PENDING)
        if (status.allowsBusinessTraffic) return null

        return HttpResponse
            .status<Map<String, Any>>(HttpStatus.SERVICE_UNAVAILABLE)
            .header(RETRY_AFTER, RETRY_AFTER_SECONDS)
            .body(
                mapOf(
                    "status" to status.name,
                    "title" to "Garage configuration unavailable",
                    "detail" to "Garage configuration has not been synchronized yet. Retry shortly.",
                    "instance" to request.path,
                ),
            )
    }

    private companion object {
        const val RETRY_AFTER = "Retry-After"
        const val RETRY_AFTER_SECONDS = "30"
    }
}
