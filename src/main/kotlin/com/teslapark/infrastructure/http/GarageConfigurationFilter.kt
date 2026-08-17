package com.teslapark.infrastructure.http

import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.GarageStateRepository
import com.teslapark.infrastructure.http.problem.ProblemCatalogue
import com.teslapark.infrastructure.http.problem.ProblemDetailFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter

@ServerFilter(patterns = ["/webhook", "/revenue", "/garage/status", "/plate-status", "/spot-status"])
class GarageConfigurationFilter(
    private val garageState: GarageStateRepository,
    private val problems: ProblemDetailFactory,
) {
    @RequestFilter
    fun rejectWhileConfigurationIsPending(request: HttpRequest<*>): HttpResponse<*>? {
        val status = runCatching { garageState.currentStatus() }.getOrDefault(GarageConfigurationStatus.PENDING)
        if (status.allowsBusinessTraffic) return null

        return problems
            .respond(
                request = request,
                kind = ProblemCatalogue.GARAGE_NOT_CONFIGURED,
                detail = "Garage configuration has not been synchronized yet. Retry shortly.",
            ).header(RETRY_AFTER, RETRY_AFTER_SECONDS)
    }

    private companion object {
        const val RETRY_AFTER = "Retry-After"
        const val RETRY_AFTER_SECONDS = "30"
    }
}
