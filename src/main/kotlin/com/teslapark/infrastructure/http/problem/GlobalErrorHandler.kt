package com.teslapark.infrastructure.http.problem

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Produces
@Singleton
@Requires(classes = [Throwable::class, ExceptionHandler::class])
class GlobalErrorHandler(
    private val problems: ProblemDetailFactory,
) : ExceptionHandler<Throwable, HttpResponse<ProblemDetail>> {
    private val logger = LoggerFactory.getLogger(GlobalErrorHandler::class.java)

    override fun handle(
        request: HttpRequest<*>,
        exception: Throwable,
    ): HttpResponse<ProblemDetail> {
        val requestId = RequestIdFilter.requestIdOf(request)
        logger.error("unexpected failure on {} requestId={}", request.path, requestId, exception)

        return problems.respond(
            request = request,
            kind = ProblemCatalogue.INTERNAL,
            detail = "Unexpected error while processing the request.",
        )
    }
}
