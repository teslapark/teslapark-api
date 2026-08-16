package com.teslapark.observability

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.MySqlSupport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.InetSocketAddress

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricsContractTest {
    private lateinit var gateControlSystem: HttpServer
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    @BeforeAll
    fun startStack() {
        gateControlSystem = HttpServer.create(InetSocketAddress(0), 0)
        gateControlSystem.createContext("/garage", ::respondWithGarage)
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("metrics_contract_test")
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
        runFullEventCycle()
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

    private fun runFullEventCycle() {
        post("""{"license_plate":"MET0001","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}""")
        post("""{"license_plate":"MET0001","lat":-23.561601,"lng":-46.655901,"event_type":"PARKED"}""")
        post("""{"license_plate":"MET0001","exit_time":"2026-08-15T14:10:00Z","event_type":"EXIT"}""")

        post("""{"license_plate":"MET0001","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}""")
        post("""{"license_plate":"MET9999","exit_time":"2026-08-15T14:10:00Z","event_type":"EXIT"}""")
        post("""{"license_plate":"MET0002","entry_time":"2026-08-15T12:00:00Z","event_type":"ENTRY"}""")
        post("""{"license_plate":"MET0002","lat":-23.999999,"lng":-46.999999,"event_type":"PARKED"}""")
    }

    private fun post(payload: String): HttpResponse<*> = call(HttpRequest.POST("/webhook", payload))

    private fun call(request: HttpRequest<*>): HttpResponse<*> =
        try {
            client.toBlocking().exchange(request, String::class.java)
        } catch (rejected: HttpClientResponseException) {
            rejected.response
        }

    private fun scrape(): String {
        val response = call(HttpRequest.GET<Any>("/metrics"))
        response.status shouldBe HttpStatus.OK
        return response.getBody(String::class.java).orElse("")
    }

    @Test
    fun `the prometheus endpoint exposes every business metric of the spec`() {
        val body = scrape()

        listOf(
            "garage_occupancy_rate",
            "garage_occupied_spots",
            "parking_entries_denied_total",
            "parking_revenue_total",
            "parking_session_duration_minutes",
            "pricing_multiplier_applied_total",
            "webhook_events_total",
            "session_anomalies_total",
        ).forEach { metric -> body shouldContain metric }
    }

    @Test
    fun `business metrics carry coherent values after a full event cycle`() {
        val body = scrape()

        body shouldContain """webhook_events_total{event_type="ENTRY",result="processed"}"""
        body shouldContain """webhook_events_total{event_type="EXIT",result="processed"}"""
        body shouldContain """webhook_events_total{event_type="PARKED",result="processed"}"""
        body shouldContain """webhook_events_total{event_type="ENTRY",result="duplicate"}"""
        body shouldContain """session_anomalies_total{type="EXIT_WITHOUT_ENTRY"}"""
        body shouldContain """session_anomalies_total{type="PARKED_UNKNOWN_SPOT"}"""
        body shouldContain """parking_revenue_total{sector="A"}"""
        body shouldContain """pricing_multiplier_applied_total{tier="LOW"}"""

        valueOf(body, """garage_total_capacity""") shouldBe CAPACITY
        (valueOf(body, """parking_session_duration_minutes_count""") ?: 0.0) shouldBe 1.0
        (valueOf(body, """parking_session_duration_minutes_sum""") ?: 0.0) shouldBe STAY_MINUTES
    }

    @Test
    fun `the http latency histogram exposes buckets for the slo panels`() {
        val body = scrape()

        body shouldContain "http_server_requests_seconds_bucket"
    }

    @Test
    fun `technical metrics required by the das are present`() {
        call(HttpRequest.GET<Any>("/revenue"))
        val body = scrape()

        listOf(
            "http_server_requests_seconds",
            "jvm_memory_used_bytes",
            "jvm_threads_live_threads",
            "system_cpu_usage",
            "process_uptime_seconds",
            "hikaricp_connections_active",
        ).forEach { metric -> body shouldContain metric }
    }

    @Test
    fun `readiness reports the database and the garage configuration`() {
        val response = call(HttpRequest.GET<Any>("/health/readiness"))

        response.status shouldBe HttpStatus.OK
        val body = response.getBody(String::class.java).orElse("")
        body shouldContain "database"
        body shouldContain "garageConfiguration"
        body shouldContain "\"status\":\"UP\""
    }

    @Test
    fun `the raw webhook payload is never written to the log`() {
        val body = scrape()

        body shouldContain "webhook_events_total"
        MySqlSupport
            .connectionTo(MySqlSupport.jdbcUrlOf("metrics_contract_test"))
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) AS total FROM webhook_event").use { rows ->
                        rows.next()
                        (rows.getInt("total") > 0) shouldBe true
                    }
                }
            }
    }

    private fun valueOf(
        body: String,
        metric: String,
    ): Double? =
        body
            .lineSequence()
            .firstOrNull { it.startsWith("$metric ") || it.startsWith("$metric{") }
            ?.substringAfterLast(' ')
            ?.toDoubleOrNull()

    private companion object {
        const val HTTP_OK = 200
        const val SYNC_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 250L
        const val CAPACITY = 30.0
        const val STAY_MINUTES = 130.0

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
