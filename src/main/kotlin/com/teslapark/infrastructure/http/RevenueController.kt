package com.teslapark.infrastructure.http

import com.teslapark.application.usecase.DailyRevenueReport
import com.teslapark.application.usecase.GetDailyRevenue
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.ClockProvider
import com.teslapark.infrastructure.http.problem.FieldError
import com.teslapark.infrastructure.http.problem.ProblemCatalogue
import com.teslapark.infrastructure.http.problem.ProblemDetailFactory
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Controller("/revenue")
@ExecuteOn(TaskExecutors.BLOCKING)
@Produces(MediaType.APPLICATION_JSON)
class RevenueController(
    private val getDailyRevenue: GetDailyRevenue,
    private val clock: ClockProvider,
    private val problems: ProblemDetailFactory,
) {
    @Get
    fun revenueByQuery(
        @QueryValue @Nullable date: String?,
        @QueryValue @Nullable sector: String?,
    ): HttpResponse<*> = revenue(date, sector)

    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    fun revenueByBody(
        @QueryValue @Nullable date: String?,
        @QueryValue @Nullable sector: String?,
        @Body request: RevenueRequest,
    ): HttpResponse<*> = revenue(date ?: request.date, sector ?: request.sector)

    private fun revenue(
        date: String?,
        sector: String?,
    ): HttpResponse<*> {
        val revenueDate =
            when (val parsed = parseDate(date)) {
                is DomainResult.Failure -> return validationFailed("date", "must match yyyy-MM-dd")
                is DomainResult.Success -> parsed.value
            }

        val sectorCode =
            when (val parsed = sector?.let { SectorCode.parse(it) }) {
                null -> null
                is DomainResult.Failure -> return validationFailed("sector", "must be 1 to 16 characters of A-Z, 0-9 or -")
                is DomainResult.Success -> parsed.value
            }

        return when (val report = getDailyRevenue.execute(revenueDate, sectorCode)) {
            is DomainResult.Failure ->
                problems.fromDomainError(currentRequest(), report.error, "Sector '${'$'}sector' does not exist in this garage.")

            is DomainResult.Success -> HttpResponse.ok(bodyOf(report.value, singleSector = sector != null))
        }
    }

    private fun currentRequest(): HttpRequest<*> =
        ServerRequestContext.currentRequest<Any>().orElseThrow { IllegalStateException("no server request in scope") }

    private fun parseDate(raw: String?): DomainResult<LocalDate?> =
        if (raw == null) {
            DomainResult.Success(null)
        } else {
            try {
                DomainResult.Success(LocalDate.parse(raw))
            } catch (invalid: DateTimeParseException) {
                DomainResult.Failure(DomainError.MalformedEventPayload(invalid.message.orEmpty()))
            }
        }

    private fun bodyOf(
        report: DailyRevenueReport,
        singleSector: Boolean,
    ): Map<String, Any?> {
        val timestamp = clock.now().toString()

        if (singleSector) {
            return mapOf(
                "amount" to report.total.amount,
                "currency" to report.currency.code,
                "timestamp" to timestamp,
                "sessions" to report.sessions,
                "free_sessions_count" to report.freeSessions,
            )
        }

        return mapOf(
            "date" to report.revenueDate.toString(),
            "currency" to report.currency.code,
            "amount" to report.total.amount,
            "timestamp" to timestamp,
            "free_sessions_count" to report.freeSessions,
            "sectors" to
                report.sectors.map { sector ->
                    mapOf(
                        "sector" to sector.sector.value,
                        "amount" to sector.amount.amount,
                        "sessions" to sector.sessions,
                        "free_sessions" to sector.freeSessions,
                    )
                },
        )
    }

    private fun validationFailed(
        field: String,
        message: String,
    ): HttpResponse<*> =
        problems.respond(
            request = currentRequest(),
            kind = ProblemCatalogue.VALIDATION,
            detail = "Invalid request parameters.",
            errors = listOf(FieldError(field, message)),
        )
}
