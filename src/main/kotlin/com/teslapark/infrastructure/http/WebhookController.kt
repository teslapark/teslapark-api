package com.teslapark.infrastructure.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.teslapark.application.usecase.GateEventOutcome
import com.teslapark.application.usecase.ProcessGateEvent
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.infrastructure.http.problem.ProblemDetailFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Controller("/webhook")
@ExecuteOn(TaskExecutors.BLOCKING)
class WebhookController(
    private val processGateEvent: ProcessGateEvent,
    private val objectMapper: ObjectMapper,
    private val problems: ProblemDetailFactory,
) {
    @Post
    @Produces(MediaType.APPLICATION_JSON)
    fun receive(
        @Body rawPayload: String,
    ): HttpResponse<*> {
        val request =
            runCatching { objectMapper.readValue(rawPayload, WebhookRequest::class.java) }
                .getOrElse { return badRequest(DomainError.MalformedEventPayload("unreadable event payload")) }

        return when (val event = request.toGateEvent()) {
            is DomainResult.Failure -> badRequest(event.error)
            is DomainResult.Success -> respond(event.value, processGateEvent.execute(event.value, rawPayload))
        }
    }

    private fun respond(
        event: GateEvent,
        outcome: GateEventOutcome,
    ): HttpResponse<*> {
        val base =
            mutableMapOf<String, Any?>(
                "event_type" to event.type.name,
                "license_plate" to event.licensePlate.value,
            )

        return when (outcome) {
            is GateEventOutcome.Accepted -> HttpResponse.ok(base + acceptedDetails(outcome))

            is GateEventOutcome.Duplicate ->
                HttpResponse.ok(
                    base +
                        mapOf(
                            "status" to "DUPLICATE",
                            "session_id" to outcome.sessionId,
                            "detail" to "Event already processed; no state change applied.",
                        ),
                )

            is GateEventOutcome.Ignored ->
                HttpResponse.ok(
                    base + mapOf("status" to "IGNORED", "anomaly" to outcome.anomaly.name, "detail" to outcome.detail),
                )

            is GateEventOutcome.Rejected -> rejected(outcome.error)
        }
    }

    private fun acceptedDetails(outcome: GateEventOutcome.Accepted): Map<String, Any?> {
        val session = outcome.session
        return buildMap {
            put("status", "ACCEPTED")
            put("session_id", session.id)
            outcome.occupancyRate?.let { put("garage_occupancy_rate", it) }
            put("applied_price_multiplier", session.priceMultiplier)
            session.sectorCode?.let { put("sector", it.value) }
            session.spotExternalId?.let { put("spot_id", it) }
            outcome.charge?.let { charge ->
                put("entry_time", session.entryTime.toString())
                put("exit_time", session.exitTime?.toString())
                put("duration_minutes", session.stay?.toMinutes())
                put("billed_hours", charge.chargeableHours)
                put("base_price", session.basePriceApplied?.amount)
                put("amount", charge.amount.amount)
                put("currency", charge.amount.currency.code)
            }
        }
    }

    private fun rejected(error: DomainError): HttpResponse<*> = problems.fromDomainError(currentRequest(), error, detailOf(error))

    private fun badRequest(error: DomainError): HttpResponse<*> =
        problems.fromDomainError(currentRequest(), error, "Invalid request parameters.")

    private fun detailOf(error: DomainError): String =
        when (error) {
            is DomainError.GarageFull -> "Occupancy is 100%. Entry denied until a vehicle exits."
            is DomainError.ExitTimeBeforeEntryTime ->
                "exit_time " + error.exitTime + " precedes entry_time " + error.entryTime + "."
            else -> "The event could not be applied to the session."
        }

    private fun currentRequest(): HttpRequest<*> =
        ServerRequestContext.currentRequest<Any>().orElseThrow { IllegalStateException("no server request in scope") }
}
