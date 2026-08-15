package com.teslapark.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.MySqlSupport
import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.GarageStateRepository
import com.teslapark.domain.port.SectorRepository
import com.teslapark.domain.port.SpotRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
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
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GarageSynchronizationTest {
    private lateinit var gateControlSystem: HttpServer
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    private val gateControlSystemAvailable = AtomicBoolean(false)

    @BeforeAll
    fun startWithGateControlSystemDown() {
        gateControlSystem = HttpServer.create(InetSocketAddress(0), 0)
        gateControlSystem.createContext("/garage", ::respond)
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("garage_synchronization_test")
        server =
            ApplicationContext.run(
                EmbeddedServer::class.java,
                MySqlSupport.datasourceProperties(jdbcUrl) +
                    mapOf(
                        "micronaut.http.services.gate-control-system.url" to
                            "http://localhost:${gateControlSystem.address.port}",
                        "micronaut.http.services.gate-control-system.read-timeout" to "2s",
                        "teslapark.gcs.retry.attempts" to "1",
                        "teslapark.gcs.retry.delay" to "10ms",
                        "teslapark.gcs.circuit-breaker.reset" to "500ms",
                        "teslapark.garage.sync.retry-delay" to "1s",
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

    private fun respond(exchange: HttpExchange) {
        val available = gateControlSystemAvailable.get()
        val body = if (available) GARAGE_PAYLOAD else """{"message":"unavailable"}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(if (available) HTTP_OK else HTTP_SERVER_ERROR, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.close()
    }

    private fun call(request: HttpRequest<*>): HttpResponse<*> =
        try {
            client.toBlocking().exchange(request, Map::class.java)
        } catch (rejected: HttpClientResponseException) {
            rejected.response
        }

    private fun bodyOf(response: HttpResponse<*>): Map<*, *> = response.getBody(Map::class.java).orElse(emptyMap<Any, Any>())

    private fun garageState() = server.applicationContext.getBean(GarageStateRepository::class.java)

    private fun awaitStatus(expected: GarageConfigurationStatus) {
        val deadline = System.currentTimeMillis() + RECOVERY_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (garageState().currentStatus() == expected) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    @Test
    @Order(1)
    fun `the application boots and stays alive with the gate control system down`() {
        server.isRunning shouldBe true
        call(HttpRequest.GET<Any>("/health/liveness")).status shouldBe HttpStatus.OK
        garageState().currentStatus() shouldBe GarageConfigurationStatus.PENDING
    }

    @Test
    @Order(2)
    fun `readiness fails while the configuration is pending`() {
        val response = call(HttpRequest.GET<Any>("/health/readiness"))

        response.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
        bodyOf(response)["status"] shouldBe "DOWN"
    }

    @Test
    @Order(3)
    fun `business endpoints answer 503 with retry after while the configuration is pending`() {
        val response = call(HttpRequest.GET<Any>("/revenue"))

        response.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
        response.header("Retry-After").shouldNotBeNull()

        val problem = response.getBody(String::class.java).orElse("")
        problem shouldContain "garage-not-configured"
        problem shouldContain "Garage configuration unavailable"
        problem shouldContain "requestId"
    }

    @Test
    @Order(4)
    fun `the background retry recovers the configuration once the gate control system returns`() {
        gateControlSystemAvailable.set(true)

        awaitStatus(GarageConfigurationStatus.SYNCED)

        garageState().currentStatus() shouldBe GarageConfigurationStatus.SYNCED
        garageState().totalCapacity() shouldBe 30
        garageState().lastSyncAt().shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `synchronization populated sectors spots and capacity without duplicating`() {
        val sectors = server.applicationContext.getBean(SectorRepository::class.java)
        val spots = server.applicationContext.getBean(SpotRepository::class.java)

        sectors.findAll() shouldHaveSize 2
        sectors.findByCode(SectorCode("A")).shouldNotBeNull().maxCapacity shouldBe 10
        sectors.findByCode(SectorCode("B")).shouldNotBeNull().maxCapacity shouldBe 20
        sectors.totalCapacity() shouldBe 30
        spots.synchronize(emptyList()) shouldHaveSize 30

        val resync = call(HttpRequest.POST("/admin/garage/sync", ""))
        resync.status shouldBe HttpStatus.OK
        bodyOf(resync)["sectors"] shouldBe 2
        bodyOf(resync)["spots"] shouldBe 30
        bodyOf(resync)["total_capacity"] shouldBe 30

        sectors.findAll() shouldHaveSize 2
        spots.synchronize(emptyList()) shouldHaveSize 30
    }

    @Test
    @Order(6)
    fun `readiness passes and business endpoints stop being rejected once synchronized`() {
        val readiness = call(HttpRequest.GET<Any>("/health/readiness"))
        readiness.status shouldBe HttpStatus.OK
        bodyOf(readiness)["status"] shouldBe "UP"

        call(HttpRequest.GET<Any>("/revenue")).status shouldBe HttpStatus.OK
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_SERVER_ERROR = 500
        const val RECOVERY_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 250L

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
