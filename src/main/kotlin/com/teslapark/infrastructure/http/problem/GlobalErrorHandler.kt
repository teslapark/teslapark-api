package com.teslapark.infrastructure.http.problem

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.codec.CodecException
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException
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

@Produces
@Singleton
@Requires(classes = [CodecException::class, ExceptionHandler::class])
class MalformedPayloadHandler(
    private val problems: ProblemDetailFactory,
) : ExceptionHandler<CodecException, HttpResponse<ProblemDetail>> {
    private val logger = LoggerFactory.getLogger(MalformedPayloadHandler::class.java)

    override fun handle(
        request: HttpRequest<*>,
        exception: CodecException,
    ): HttpResponse<ProblemDetail> {
        logger.warn("malformed payload on {}", request.path)

        return problems.respond(
            request = request,
            kind = ProblemCatalogue.VALIDATION,
            detail = "Invalid request parameters.",
            errors = listOf(FieldError("body", "must be a well formed JSON document")),
        )
    }
}

@Produces
@Singleton
@Requires(classes = [UnsatisfiedRouteException::class, ExceptionHandler::class])
class MissingArgumentHandler(
    private val problems: ProblemDetailFactory,
) : ExceptionHandler<UnsatisfiedRouteException, HttpResponse<ProblemDetail>> {
    private val logger = LoggerFactory.getLogger(MissingArgumentHandler::class.java)

    override fun handle(
        request: HttpRequest<*>,
        exception: UnsatisfiedRouteException,
    ): HttpResponse<ProblemDetail> {
        logger.warn("missing argument {} on {}", exception.argument.name, request.path)

        return problems.respond(
            request = request,
            kind = ProblemCatalogue.VALIDATION,
            detail = "Invalid request parameters.",
            errors = listOf(FieldError(exception.argument.name, "is required")),
        )
    }
}
