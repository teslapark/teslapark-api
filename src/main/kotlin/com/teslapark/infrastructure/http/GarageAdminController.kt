package com.teslapark.infrastructure.http

import com.teslapark.application.usecase.SyncGarageConfiguration
import com.teslapark.domain.error.DomainResult
import com.teslapark.infrastructure.http.problem.ProblemDetailFactory
import com.teslapark.infrastructure.http.problem.ProblemKind
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Controller("/admin/garage")
@ExecuteOn(TaskExecutors.BLOCKING)
class GarageAdminController(
    private val syncGarageConfiguration: SyncGarageConfiguration,
    private val problems: ProblemDetailFactory,
) {
    @Post("/sync")
    @Produces(MediaType.APPLICATION_JSON)
    fun synchronize(): HttpResponse<*> =
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
                problems.respond(
                    request = ServerRequestContext.currentRequest<Any>().orElseThrow(),
                    kind = ProblemKind(HttpStatus.BAD_GATEWAY, "gate-control-system-unavailable", "Gate control system unavailable"),
                    detail = "Could not read the garage configuration from the gate control system.",
                )
        }
}
