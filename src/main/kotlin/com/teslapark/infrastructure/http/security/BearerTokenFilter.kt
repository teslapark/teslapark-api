package com.teslapark.infrastructure.http.security

import com.teslapark.infrastructure.http.problem.ProblemCatalogue
import com.teslapark.infrastructure.http.problem.ProblemDetailFactory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter

@ServerFilter(patterns = ["/revenue", "/admin/**", "/metrics"])
@Requires(property = "teslapark.security.enabled", value = "true")
class BearerTokenFilter(
    private val problems: ProblemDetailFactory,
    private val configuration: SecurityConfiguration,
) {
    @RequestFilter
    fun authorize(request: HttpRequest<*>): HttpResponse<*>? {
        val presented =
            request.headers[AUTHORIZATION]
                ?.takeIf { it.startsWith(BEARER_PREFIX) }
                ?.removePrefix(BEARER_PREFIX)
                ?.trim()
                ?: return problems.respond(request, ProblemCatalogue.UNAUTHORIZED, "Missing or invalid bearer token.")

        val granted =
            configuration.scopesOf(presented)
                ?: return problems.respond(request, ProblemCatalogue.UNAUTHORIZED, "Missing or invalid bearer token.")

        val required = requiredScopeFor(request.path)
        if (required != null && required !in granted) {
            return problems.respond(request, ProblemCatalogue.FORBIDDEN, "Token lacks the required scope '$required'.")
        }

        return null
    }

    private fun requiredScopeFor(path: String): String? =
        when {
            path.startsWith("/admin") -> ADMIN_SCOPE
            path.startsWith("/revenue") -> REVENUE_SCOPE
            path.startsWith("/metrics") -> METRICS_SCOPE
            else -> null
        }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val ADMIN_SCOPE = "garage:admin"
        const val REVENUE_SCOPE = "revenue:read"
        const val METRICS_SCOPE = "metrics:read"
    }
}
