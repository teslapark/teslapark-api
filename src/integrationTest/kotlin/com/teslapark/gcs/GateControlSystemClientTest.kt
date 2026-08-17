package com.teslapark.gcs

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.GarageConfigurationProvider
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

class GateControlSystemClientTest {
    private var server: HttpServer? = null
    private var context: ApplicationContext? = null

    @AfterEach
    fun tearDown() {
        context?.close()
        server?.stop(0)
    }

    private fun startServer(handler: (HttpExchange) -> Unit): Int {
        val started = HttpServer.create(InetSocketAddress(0), 0)
        started.createContext("/garage") { exchange -> exchange.use(handler) }
        started.start()
        server = started
        return started.address.port
    }

    private fun HttpExchange.use(handler: (HttpExchange) -> Unit) {
        try {
            handler(this)
        } finally {
            close()
        }
    }

    private fun HttpExchange.respond(
        status: Int,
        body: String,
    ) {
        responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }

    private fun providerAt(
        port: Int,
        overrides: Map<String, Any> = emptyMap(),
    ): GarageConfigurationProvider {
        val started =
            ApplicationContext.run(
                mapOf(
                    "micronaut.http.services.gate-control-system.url" to "http://localhost:$port",
                    "micronaut.http.services.gate-control-system.connect-timeout" to "1s",
                    "micronaut.http.services.gate-control-system.read-timeout" to "1s",
                    "teslapark.gcs.retry.attempts" to "3",
                    "teslapark.gcs.retry.delay" to "50ms",
                    "datasources.default.enabled" to false,
                    "flyway.datasources.default.enabled" to false,
                    "teslapark.garage.sync.enabled" to false,
                ) + overrides,
                "test",
            )
        context = started
        return started.getBean(GarageConfigurationProvider::class.java)
    }

    @Test
    fun `maps the real gate control system payload into domain objects`() {
        val port = startServer { it.respond(HTTP_OK, SNAKE_CASE_PAYLOAD) }

        val configuration = providerAt(port).fetchConfiguration().valueOrNull().shouldNotBeNull()

        configuration.garage.sectors shouldHaveSize 2
        configuration.spots shouldHaveSize 30
        configuration.totalCapacity shouldBe 30

        val sectorA = configuration.garage.sectorBy(SectorCode("A")).shouldNotBeNull()
        sectorA.basePrice shouldBe Money.of("40.50")
        sectorA.maxCapacity shouldBe 10
        sectorA.openHour shouldBe LocalTime.of(0, 0)
        sectorA.closeHour shouldBe LocalTime.of(23, 59)
        sectorA.durationLimit shouldBe Duration.ofMinutes(1440)

        val sectorB = configuration.garage.sectorBy(SectorCode("B")).shouldNotBeNull()
        sectorB.basePrice shouldBe Money.of("4.10")
        sectorB.durationLimit shouldBe Duration.ofMinutes(60)

        val firstSpot = configuration.spots.first()
        firstSpot.externalId shouldBe 1L
        firstSpot.sectorCode shouldBe SectorCode("A")
        firstSpot.coordinates shouldBe Coordinates.of("-23.561684", "-46.655981")
        firstSpot.occupied shouldBe false
        configuration.spotsOf(SectorCode("B")) shouldHaveSize 20
    }

    @Test
    fun `camel case and snake case payloads produce the same configuration`() {
        val snakePort = startServer { it.respond(HTTP_OK, SNAKE_CASE_PAYLOAD) }
        val fromSnake = providerAt(snakePort).fetchConfiguration().valueOrNull().shouldNotBeNull()
        context?.close()
        server?.stop(0)

        val camelPort = startServer { it.respond(HTTP_OK, CAMEL_CASE_PAYLOAD) }
        val fromCamel = providerAt(camelPort).fetchConfiguration().valueOrNull().shouldNotBeNull()

        fromCamel.garage.sectors shouldBe fromSnake.garage.sectors
        fromCamel.spots shouldBe fromSnake.spots
    }

    @Test
    fun `the minimal payload documented in the challenge statement is accepted`() {
        val port = startServer { it.respond(HTTP_OK, STATEMENT_PAYLOAD) }

        val configuration = providerAt(port).fetchConfiguration().valueOrNull().shouldNotBeNull()

        configuration.garage.sectors shouldHaveSize 1
        configuration.spots shouldHaveSize 1
        configuration.totalCapacity shouldBe 100

        val sector = configuration.garage.sectorBy(SectorCode("A")).shouldNotBeNull()
        sector.basePrice shouldBe Money.of("10.00")
        sector.maxCapacity shouldBe 100
        sector.openHour shouldBe LocalTime.of(0, 0)
        sector.closeHour shouldBe LocalTime.of(23, 59)
        sector.durationLimit shouldBe Duration.ofMinutes(1440)

        configuration.spots.single().coordinates shouldBe Coordinates.of("-23.561684", "-46.655981")
    }

    @Test
    fun `a server error is retried and then reported as a domain error`() {
        val attempts = AtomicInteger()
        val port =
            startServer { exchange ->
                attempts.incrementAndGet()
                exchange.respond(HTTP_SERVER_ERROR, """{"message":"boom"}""")
            }

        providerAt(port).fetchConfiguration().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable

        (attempts.get() >= EXPECTED_ATTEMPTS) shouldBe true
    }

    @Test
    fun `a read timeout is reported as a domain error and never leaks the http exception`() {
        val port =
            startServer { exchange ->
                Thread.sleep(BEYOND_READ_TIMEOUT_MILLIS)
                exchange.respond(HTTP_OK, SNAKE_CASE_PAYLOAD)
            }

        providerAt(port).fetchConfiguration().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable
    }

    @Test
    fun `an unreachable gate control system is reported as a domain error`() {
        val port = startServer { it.respond(HTTP_OK, SNAKE_CASE_PAYLOAD) }
        server?.stop(0)

        providerAt(port).fetchConfiguration().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_SERVER_ERROR = 500
        const val EXPECTED_ATTEMPTS = 3
        const val BEYOND_READ_TIMEOUT_MILLIS = 2500L

        private fun spotsJson(camelCase: Boolean): String {
            val idKey = if (camelCase) "id" else "id"
            return (1..30).joinToString(",") { index ->
                val sector = if (index <= 10) "A" else "B"
                val latitude = "-23.5616%02d".format(85 - index)
                val longitude = "-46.6559%02d".format(82 - index)
                """{"$idKey":$index,"sector":"$sector","lat":$latitude,"lng":$longitude,"occupied":false}"""
            }
        }

        val STATEMENT_PAYLOAD =
            """
            {
              "garage": [
                {"sector": "A", "basePrice": 10.0, "max_capacity": 100}
              ],
              "spots": [
                {"id": 1, "sector": "A", "lat": -23.561684, "lng": -46.655981}
              ]
            }
            """.trimIndent()

        val SNAKE_CASE_PAYLOAD =
            """
            {"garage":[
              {"sector":"A","base_price":40.5,"max_capacity":10,"open_hour":"00:00","close_hour":"23:59","duration_limit_minutes":1440},
              {"sector":"B","base_price":4.1,"max_capacity":20,"open_hour":"08:00","close_hour":"23:59","duration_limit_minutes":60}
            ],"spots":[${spotsJson(camelCase = false)}]}
            """.trimIndent()

        val CAMEL_CASE_PAYLOAD =
            """
            {"garage":[
              {"sector":"A","basePrice":40.5,"maxCapacity":10,"openHour":"00:00","closeHour":"23:59","durationLimitMinutes":1440},
              {"sector":"B","basePrice":4.1,"maxCapacity":20,"openHour":"08:00","closeHour":"23:59","durationLimitMinutes":60}
            ],"spots":[${spotsJson(camelCase = true)}]}
            """.trimIndent()
    }
}
