package com.teslapark.e2e

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
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EndToEndTest {
    private lateinit var gateControlSystem: GenericContainer<*>
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    private val exactMapper =
        com.fasterxml.jackson.databind
            .ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

    @BeforeAll
    fun startStack() {
        gateControlSystem =
            GenericContainer(DockerImageName.parse(GCS_IMAGE))
                .withExposedPorts(GCS_PORT)
                .waitingFor(Wait.forListeningPort())
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("end_to_end_test")
        server =
            ApplicationContext.run(
                EmbeddedServer::class.java,
                MySqlSupport.datasourceProperties(jdbcUrl) +
                    mapOf(
                        "micronaut.http.services.gate-control-system.url" to
                            "http://${gateControlSystem.host}:${gateControlSystem.getMappedPort(GCS_PORT)}",
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
        gateControlSystem.stop()
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

    private fun post(payload: String): HttpResponse<*> = call(HttpRequest.POST("/webhook", payload))

    private fun textOf(response: HttpResponse<*>): String = response.getBody(String::class.java).orElse("")

    private fun jsonOf(response: HttpResponse<*>): Map<*, *> = exactMapper.readValue(textOf(response), Map::class.java)

    private fun entry(plate: String) = """{"license_plate":"$plate","entry_time":"$ENTRY_TIME","event_type":"ENTRY"}"""

    private fun exit(plate: String) = """{"license_plate":"$plate","exit_time":"$EXIT_TIME","event_type":"EXIT"}"""

    private fun plateAt(index: Int) = "E2E" + "%04d".format(index)

    @Test
    @Order(1)
    fun `the real gate control system configuration is synchronized on startup`() {
        val status = jsonOf(call(HttpRequest.POST("/admin/garage/sync", "")))

        status["status"] shouldBe "SYNCED"
        status["sectors"] shouldBe EXPECTED_SECTORS
        status["spots"] shouldBe CAPACITY
        status["total_capacity"] shouldBe CAPACITY
    }

    @Test
    @Order(2)
    fun `filling the garage denies the next entry and a single exit reopens it`() {
        (1..CAPACITY).forEach { index ->
            jsonOf(post(entry(plateAt(index))))["status"] shouldBe "ACCEPTED"
        }

        val denied = post(entry(plateAt(CAPACITY + 1)))
        denied.status shouldBe HttpStatus.CONFLICT
        textOf(denied) shouldContain "garage-full"

        jsonOf(post(exit(plateAt(1))))["status"] shouldBe "ACCEPTED"

        jsonOf(post(entry(plateAt(CAPACITY + 1))))["status"] shouldBe "ACCEPTED"
    }

    @Test
    @Order(3)
    fun `the revenue matches the sum of every exit of the day`() {
        (2..CAPACITY).forEach { index -> post(exit(plateAt(index))) }

        val report = jsonOf(call(HttpRequest.GET<Any>("/revenue?date=$REVENUE_DATE")))
        val sectors = report["sectors"] as List<*>

        val breakdown = sectors.sumOf { (it as Map<*, *>)["amount"] as BigDecimal }
        breakdown.compareTo(report["amount"] as BigDecimal) shouldBe 0

        sectors.sumOf { (it as Map<*, *>)["sessions"] as Int } shouldBe CAPACITY

        report["currency"] shouldBe "BRL"
    }

    @Test
    @Order(4)
    fun `the occupancy returns to the reopened state and the metrics agree with the database`() {
        val metrics = textOf(call(HttpRequest.GET<Any>("/metrics")))

        metrics shouldContain "garage_total_capacity $CAPACITY.0"
        metrics shouldContain "parking_entries_denied_total 1.0"
        metrics shouldContain """webhook_events_total{event_type="ENTRY",result="rejected"} 1.0"""

        val readiness = textOf(call(HttpRequest.GET<Any>("/health/readiness")))
        readiness shouldContain """"status":"UP""""
    }

    private companion object {
        const val GCS_IMAGE = "cfontes0estapar/garage-sim:1.0.0"
        const val GCS_PORT = 3000
        const val CAPACITY = 30
        const val EXPECTED_SECTORS = 2
        const val SYNC_TIMEOUT_MILLIS = 120_000L
        const val POLL_INTERVAL_MILLIS = 500L
        const val ENTRY_TIME = "2026-08-15T12:00:00Z"
        const val EXIT_TIME = "2026-08-15T14:10:00Z"
        const val REVENUE_DATE = "2026-08-15"
    }
}
