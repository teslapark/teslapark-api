package com.teslapark.errors

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.MySqlSupport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.net.InetSocketAddress

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SecurityContractTest {
    private lateinit var gateControlSystem: HttpServer
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    @BeforeAll
    fun startStack() {
        gateControlSystem = HttpServer.create(InetSocketAddress(0), 0)
        gateControlSystem.createContext("/garage", ::respondWithGarage)
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("security_contract_test")
        server =
            ApplicationContext.run(
                EmbeddedServer::class.java,
                MySqlSupport.datasourceProperties(jdbcUrl) +
                    mapOf(
                        "micronaut.http.services.gate-control-system.url" to
                            "http://localhost:${gateControlSystem.address.port}",
                        "teslapark.garage.sync.retry-delay" to "1s",
                        "teslapark.revenue.reconciliation.enabled" to false,
                        "teslapark.security.enabled" to true,
                        "teslapark.security.tokens.$FULL_TOKEN" to "garage:admin,revenue:read",
                        "teslapark.security.tokens.$LIMITED_TOKEN" to "garage:admin",
                        "teslapark.rate-limit.enabled" to true,
                        "teslapark.rate-limit.requests" to RATE_LIMIT,
                        "teslapark.rate-limit.window" to "5m",
                    ),
                "test",
            )
        client = HttpClient.create(server.url)
    }

    @AfterAll
    fun stop() {
        client.close()
        server.close()
        gateControlSystem.stop(0)
    }

    private fun respondWithGarage(exchange: HttpExchange) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        val bytes = GARAGE_PAYLOAD.toByteArray()
        exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.close()
    }

    private fun bearer(
        request: MutableHttpRequest<*>,
        token: String,
    ): MutableHttpRequest<*> = request.header("Authorization", "Bearer $token")

    private fun call(request: HttpRequest<*>): HttpResponse<*> =
        try {
            client.toBlocking().exchange(request, String::class.java)
        } catch (rejected: HttpClientResponseException) {
            rejected.response
        }

    private fun textOf(response: HttpResponse<*>): String = response.getBody(String::class.java).orElse("")

    private fun assertProblem(
        response: HttpResponse<*>,
        status: HttpStatus,
        slug: String,
    ) {
        response.status shouldBe status
        response.contentType.get().toString() shouldBe PROBLEM_JSON
        textOf(response) shouldContain """"type":"https://api.teslapark.local/errors/$slug""""
        textOf(response) shouldContain """"requestId":"""
    }

    @Test
    @Order(1)
    fun `401 when the bearer token is missing or unknown`() {
        assertProblem(call(HttpRequest.GET<Any>("/revenue")), HttpStatus.UNAUTHORIZED, "unauthorized")

        assertProblem(
            call(bearer(HttpRequest.GET<Any>("/revenue"), "not-a-token")),
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
        )

        assertProblem(
            call(HttpRequest.POST("/admin/garage/sync", "")),
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
        )
    }

    @Test
    @Order(2)
    fun `403 when the token is valid but lacks the required scope`() {
        val response = call(bearer(HttpRequest.GET<Any>("/revenue"), LIMITED_TOKEN))

        assertProblem(response, HttpStatus.FORBIDDEN, "forbidden")
        textOf(response) shouldContain "revenue:read"
    }

    @Test
    @Order(3)
    fun `the same token is accepted where its scope applies`() {
        call(bearer(HttpRequest.POST("/admin/garage/sync", ""), LIMITED_TOKEN)).status shouldNotBe
            HttpStatus.FORBIDDEN
    }

    @Test
    @Order(4)
    fun `the webhook is not behind the bearer gate`() {
        call(HttpRequest.POST("/webhook", "not json")).status shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    @Order(LAST)
    fun `429 with retry after once the rate limit is exceeded`() {
        var throttled: HttpResponse<*>? = null

        repeat(RATE_LIMIT * 2) {
            val response = call(bearer(HttpRequest.GET<Any>("/revenue"), FULL_TOKEN))
            if (response.status == HttpStatus.TOO_MANY_REQUESTS) throttled = response
        }

        assertProblem(throttled!!, HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded")
        throttled!!.header("Retry-After") shouldNotBe null
    }

    private companion object {
        const val HTTP_OK = 200
        const val LAST = Int.MAX_VALUE
        const val PROBLEM_JSON = "application/problem+json"
        const val FULL_TOKEN = "fulltoken"
        const val LIMITED_TOKEN = "limitedtoken"
        const val RATE_LIMIT = 20

        val GARAGE_PAYLOAD =
            """
            {"garage":[
              {"sector":"A","base_price":40.5,"max_capacity":10,"open_hour":"00:00","close_hour":"23:59","duration_limit_minutes":1440},
              {"sector":"B","base_price":4.1,"max_capacity":20,"open_hour":"08:00","close_hour":"23:59","duration_limit_minutes":60}
            ],"spots":[${
                (1..30).joinToString(",") { index ->
                    val sector = if (index <= 10) "A" else "B"
                    val offset = "%02d".format(index)
                    """{"id":$index,"sector":"$sector","lat":-23.5616$offset,"lng":-46.6559$offset,"occupied":false}"""
                }
            }]}
            """.trimIndent()
    }
}
