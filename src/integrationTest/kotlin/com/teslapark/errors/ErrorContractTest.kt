package com.teslapark.errors

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.MySqlSupport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
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
class ErrorContractTest {
    private lateinit var gateControlSystem: HttpServer
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    @BeforeAll
    fun startStack() {
        gateControlSystem = HttpServer.create(InetSocketAddress(0), 0)
        gateControlSystem.createContext("/garage", ::respondWithGarage)
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("error_contract_test")
        server =
            ApplicationContext.run(
                EmbeddedServer::class.java,
                MySqlSupport.datasourceProperties(jdbcUrl) +
                    mapOf(
                        "micronaut.http.services.gate-control-system.url" to
                            "http://localhost:${gateControlSystem.address.port}",
                        "teslapark.garage.sync.retry-delay" to "1s",
                        "teslapark.revenue.reconciliation.enabled" to false,
                    ),
                "test",
            )
        client = HttpClient.create(server.url)
        awaitSynchronization()
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

    private fun awaitSynchronization() {
        val deadline = System.currentTimeMillis() + SYNC_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (call(HttpRequest.GET<Any>("/revenue")).status != HttpStatus.SERVICE_UNAVAILABLE) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    private fun call(request: HttpRequest<*>): HttpResponse<*> =
        try {
            client.toBlocking().exchange(request, String::class.java)
        } catch (rejected: HttpClientResponseException) {
            rejected.response
        }

    private fun textOf(response: HttpResponse<*>): String = response.getBody(String::class.java).orElse("")

    private fun assertProblemShape(
        response: HttpResponse<*>,
        status: HttpStatus,
        slug: String,
    ) {
        response.status shouldBe status
        response.contentType.get().toString() shouldContain "application/problem+json"

        val body = textOf(response)
        body shouldContain """"type":"https://api.teslapark.local/errors/$slug""""
        body shouldContain """"status":${status.code}"""
        body shouldContain """"instance":"""
        body shouldContain """"timestamp":"""
        body shouldContain """"requestId":"""
    }

    @Test
    fun `400 on a malformed payload`() {
        val response = call(HttpRequest.POST("/webhook", """{"license_plate":"MISS0001","event_type":"ENTRY"}"""))

        assertProblemShape(response, HttpStatus.BAD_REQUEST, "validation")
        textOf(response) shouldContain """"field":"entry_time""""
    }

    @Test
    fun `400 on an oversized license plate`() {
        val payload =
            """{"license_plate":"${"A".repeat(OVERSIZED_PLATE)}","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}"""

        val response = call(HttpRequest.POST("/webhook", payload))

        assertProblemShape(response, HttpStatus.BAD_REQUEST, "validation")
        textOf(response) shouldContain """"field":"license_plate""""
    }

    @Test
    fun `404 on an unknown sector`() {
        val response = call(HttpRequest.GET<Any>("/revenue?date=2026-08-15&sector=ZZ"))

        assertProblemShape(response, HttpStatus.NOT_FOUND, "sector-not-found")
    }

    @Test
    @Order(LAST)
    fun `409 when the garage is full`() {
        (1..CAPACITY).forEach { index ->
            val plate = "FULL" + "%04d".format(index)
            call(
                HttpRequest.POST(
                    "/webhook",
                    """{"license_plate":"$plate","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}""",
                ),
            )
        }

        val response =
            call(
                HttpRequest.POST(
                    "/webhook",
                    """{"license_plate":"OVER0001","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}""",
                ),
            )

        assertProblemShape(response, HttpStatus.CONFLICT, "garage-full")
    }

    @Test
    fun `422 when the exit precedes the entry`() {
        call(
            HttpRequest.POST(
                "/webhook",
                """{"license_plate":"EARLY001","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}""",
            ),
        )

        val response =
            call(
                HttpRequest.POST(
                    "/webhook",
                    """{"license_plate":"EARLY001","exit_time":"2026-08-15T11:00:00Z","event_type":"EXIT"}""",
                ),
            )

        assertProblemShape(response, HttpStatus.UNPROCESSABLE_ENTITY, "invalid-exit-time")
    }

    @Test
    fun `500 never leaks the stacktrace the sql or a table name`() {
        val response = call(HttpRequest.GET<Any>("/test/unexpected-failure"))

        assertProblemShape(response, HttpStatus.INTERNAL_SERVER_ERROR, "internal")

        val body = textOf(response)
        body shouldNotContain "Exception"
        body shouldNotContain "SQL"
        body shouldNotContain "parking_session"
        body shouldNotContain "com.mysql"
        body shouldContain """"detail":"Unexpected error while processing the request.""""
    }

    @Test
    fun `the client request id is returned unchanged`() {
        val given = "6d1f9b2e-4c3a-4f0b-9a1d-2f7e1c8b5a44"
        val response =
            call(HttpRequest.GET<Any>("/test/unexpected-failure").header("X-Request-Id", given))

        response.header("X-Request-Id") shouldBe given
        textOf(response) shouldContain """"requestId":"$given""""
    }

    @Test
    fun `a generated request id is present when the client omits it`() {
        call(HttpRequest.GET<Any>("/revenue")).header("X-Request-Id") shouldNotBe null

        val failed = call(HttpRequest.GET<Any>("/test/unexpected-failure"))
        failed.header("X-Request-Id") shouldNotBe null
        textOf(failed) shouldContain """"requestId":"""
    }

    @Test
    fun `every endpoint answers errors with the same media type`() {
        listOf(
            call(HttpRequest.GET<Any>("/revenue?date=nope")),
            call(HttpRequest.POST("/webhook", "not json")),
            call(HttpRequest.GET<Any>("/test/unexpected-failure")),
        ).forEach { response ->
            response.contentType.get().toString() shouldBe PROBLEM_JSON
            textOf(response) shouldContain """"title":"""
            textOf(response) shouldContain """"type":"https://api.teslapark.local/errors/"""
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val LAST = Int.MAX_VALUE
        const val PROBLEM_JSON = "application/problem+json"
        const val SYNC_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 250L
        const val CAPACITY = 30
        const val OVERSIZED_PLATE = 64

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
