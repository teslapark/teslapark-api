package com.teslapark.infrastructure.http.problem

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import org.slf4j.MDC
import java.util.UUID

@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
class RequestIdFilter {
    @RequestFilter
    fun assignRequestId(request: HttpRequest<*>) {
        val requestId = request.headers[HEADER]?.takeIf { it.isNotBlank() && it.length <= MAX_LENGTH } ?: newRequestId()
        request.setAttribute(ATTRIBUTE, requestId)
        MDC.put(MDC_KEY, requestId)
    }

    @ResponseFilter
    fun propagateRequestId(
        request: HttpRequest<*>,
        response: MutableHttpResponse<*>,
    ) {
        requestIdOf(request)?.let { response.header(HEADER, it) }
        MDC.remove(MDC_KEY)
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"

        private const val ATTRIBUTE = "teslapark.requestId"
        private const val MAX_LENGTH = 128

        fun requestIdOf(request: HttpRequest<*>): String? =
            request.getAttribute(ATTRIBUTE, String::class.java).orElseGet {
                request.headers[HEADER]?.takeIf { it.isNotBlank() }
            }

        private fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
