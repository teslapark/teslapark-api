package com.teslapark.infrastructure.http.problem

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.codec.CodecException
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

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
