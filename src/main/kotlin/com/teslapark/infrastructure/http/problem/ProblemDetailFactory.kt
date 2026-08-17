package com.teslapark.infrastructure.http.problem

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.port.ClockProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton

@Singleton
class ProblemDetailFactory(
    private val clock: ClockProvider,
) {
    private val problemJson = MediaType("application/problem+json")

    fun fromDomainError(
        request: HttpRequest<*>,
        error: DomainError,
        detail: String,
    ): MutableHttpResponse<ProblemDetail> = respond(request, ProblemCatalogue.kindOf(error), detail, ProblemCatalogue.fieldErrorsOf(error))

    fun respond(
        request: HttpRequest<*>,
        kind: ProblemKind,
        detail: String,
        errors: List<FieldError> = emptyList(),
    ): MutableHttpResponse<ProblemDetail> {
        val problem =
            ProblemDetail(
                type = ProblemCatalogue.typeOf(kind.slug),
                title = kind.title,
                status = kind.status.code,
                detail = detail,
                instance = request.path,
                timestamp = clock.now().toString(),
                requestId = RequestIdFilter.requestIdOf(request),
                errors = errors,
            )

        return HttpResponse
            .status<ProblemDetail>(kind.status)
            .contentType(problemJson)
            .body(problem)
    }
}
