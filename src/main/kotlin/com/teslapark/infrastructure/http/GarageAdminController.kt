package com.teslapark.infrastructure.http

import com.teslapark.application.usecase.SyncGarageConfiguration
import com.teslapark.domain.error.DomainResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Controller("/admin/garage")
@ExecuteOn(TaskExecutors.BLOCKING)
class GarageAdminController(
    private val syncGarageConfiguration: SyncGarageConfiguration,
) {
    @Post("/sync")
    @Produces(MediaType.APPLICATION_JSON)
    fun synchronize(): HttpResponse<Map<String, Any>> =
        when (val result = syncGarageConfiguration.execute()) {
            is DomainResult.Success -> {
                val summary = result.value
                val body =
                    mapOf(
                        "status" to summary.status.name,
                        "sectors" to summary.sectors,
                        "spots" to summary.spots,
                        "total_capacity" to summary.totalCapacity,
                        "synced_at" to summary.syncedAt.toString(),
                    )
                if (summary.firstSynchronization) HttpResponse.created(body) else HttpResponse.ok(body)
            }

            is DomainResult.Failure ->
                HttpResponse
                    .status<Map<String, Any>>(HttpStatus.BAD_GATEWAY)
                    .body(
                        mapOf(
                            "title" to "Gate control system unavailable",
                            "detail" to "Could not read the garage configuration from the gate control system.",
                        ),
                    )
        }
}
