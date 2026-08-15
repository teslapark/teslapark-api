package com.teslapark.infrastructure.http

import com.teslapark.application.usecase.DailyRevenueReport
import com.teslapark.application.usecase.GetDailyRevenue
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.ClockProvider
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
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
) {
    @Get
    fun revenueByQuery(
        @QueryValue @Nullable date: String?,
        @QueryValue @Nullable sector: String?,
    ): HttpResponse<Map<String, Any?>> = revenue(date, sector)

    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    fun revenueByBody(
        @QueryValue @Nullable date: String?,
        @QueryValue @Nullable sector: String?,
        @Body request: RevenueRequest,
    ): HttpResponse<Map<String, Any?>> = revenue(date ?: request.date, sector ?: request.sector)

    private fun revenue(
        date: String?,
        sector: String?,
    ): HttpResponse<Map<String, Any?>> {
        val revenueDate =
            when (val parsed = parseDate(date)) {
                is DomainResult.Failure -> return validationFailed("date", "must match yyyy-MM-dd")
                is DomainResult.Success -> parsed.value
            }

        return when (val report = getDailyRevenue.execute(revenueDate, sector?.let { SectorCode(it) })) {
            is DomainResult.Failure -> sectorNotFound(report.error)
            is DomainResult.Success -> HttpResponse.ok(bodyOf(report.value, singleSector = sector != null))
        }
    }

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

    private fun sectorNotFound(error: DomainError): HttpResponse<Map<String, Any?>> =
        HttpResponse
            .status<Map<String, Any?>>(HttpStatus.NOT_FOUND)
            .body(mapOf("title" to "Sector not found", "detail" to error.toString()))

    private fun validationFailed(
        field: String,
        message: String,
    ): HttpResponse<Map<String, Any?>> =
        HttpResponse
            .status<Map<String, Any?>>(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "title" to "Validation failed",
                    "detail" to "Invalid request parameters.",
                    "errors" to listOf(mapOf("field" to field, "message" to message)),
                ),
            )
}
