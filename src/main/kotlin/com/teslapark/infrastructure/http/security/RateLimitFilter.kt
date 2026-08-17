package com.teslapark.infrastructure.http.security

import com.teslapark.domain.port.ClockProvider
import com.teslapark.infrastructure.http.problem.ProblemCatalogue
import com.teslapark.infrastructure.http.problem.ProblemDetailFactory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@ServerFilter(patterns = ["/webhook", "/revenue"])
@Requires(property = "teslapark.rate-limit.enabled", value = "true")
class RateLimitFilter(
    private val problems: ProblemDetailFactory,
    private val clock: ClockProvider,
    @Value("\${teslapark.rate-limit.requests:600}") private val allowedRequests: Int,
    @Value("\${teslapark.rate-limit.window:1m}") private val window: Duration,
) {
    private val windows = ConcurrentHashMap<String, RequestWindow>()

    @RequestFilter
    fun throttle(request: HttpRequest<*>): HttpResponse<*>? {
        val origin = request.remoteAddress.address?.hostAddress ?: UNKNOWN_ORIGIN
        val now = clock.now().toEpochMilli()

        val current =
            windows.compute(origin) { _, existing ->
                if (existing == null || now - existing.startedAt >= window.toMillis()) {
                    RequestWindow(startedAt = now, count = 1)
                } else {
                    RequestWindow(startedAt = existing.startedAt, count = existing.count + 1)
                }
            }!!

        if (current.count <= allowedRequests) return null

        return problems
            .respond(request, ProblemCatalogue.RATE_LIMITED, "Rate limit exceeded for this origin.")
            .header(RETRY_AFTER, window.toSeconds().toString())
    }

    private data class RequestWindow(
        val startedAt: Long,
        val count: Int,
    )

    private companion object {
        const val RETRY_AFTER = "Retry-After"
        const val UNKNOWN_ORIGIN = "unknown"
    }
}
