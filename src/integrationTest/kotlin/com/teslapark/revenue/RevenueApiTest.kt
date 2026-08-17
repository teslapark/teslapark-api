package com.teslapark.revenue

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.MySqlSupport
import com.teslapark.application.usecase.ReconcileDailyRevenue
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.domain.port.RevenueRepository
import io.kotest.matchers.shouldBe
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
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevenueApiTest {
    private lateinit var gateControlSystem: HttpServer
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    private val reportDate = LocalDate.parse("2026-08-15")

    @BeforeAll
    fun startStack() {
        gateControlSystem = HttpServer.create(InetSocketAddress(0), 0)
        gateControlSystem.createContext("/garage", ::respondWithGarage)
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("revenue_api_test")
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

        seedRevenue()
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
            if (get("/revenue").status != HttpStatus.SERVICE_UNAVAILABLE) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    private fun seedRevenue() {
        val revenue = server.applicationContext.getBean(RevenueRepository::class.java)
        revenue.accumulate(RevenueEntry(SectorCode("A"), reportDate, Money.of("121.50")))
        revenue.accumulate(RevenueEntry(SectorCode("A"), reportDate, Money.of("40.50")))
        revenue.accumulate(RevenueEntry(SectorCode("B"), reportDate, Money.of("13.53")))
        revenue.accumulate(RevenueEntry(SectorCode("B"), reportDate, Money.zero()))
    }

    private fun get(uri: String): HttpResponse<*> =
        try {
            client.toBlocking().exchange(HttpRequest.GET<Any>(uri), Map::class.java)
        } catch (rejected: HttpClientResponseException) {
            rejected.response
        }

    private fun getJson(
        uri: String,
        jsonBody: String? = null,
    ): Map<String, Any?> {
        val method = if (jsonBody == null) "GET" else "POST"
        val publisher =
            if (jsonBody == null) {
                java.net.http.HttpRequest.BodyPublishers
                    .noBody()
            } else {
                java.net.http.HttpRequest.BodyPublishers
                    .ofString(jsonBody)
            }

        val request =
            java.net.http.HttpRequest
                .newBuilder(java.net.URI.create(server.url.toString() + uri))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build()

        val response =
            java.net.http.HttpClient
                .newHttpClient()
                .send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers
                        .ofString(),
                )

        @Suppress("UNCHECKED_CAST")
        return exactMapper.readValue(response.body(), Map::class.java) as Map<String, Any?>
    }

    @Test
    fun `query params and the legacy json body request produce the same result`() {
        val fromQuery = getJson("/revenue?date=2026-08-15&sector=A")
        val fromBody = getJson("/revenue", """{"date":"2026-08-15","sector":"A"}""")

        fromQuery.minus("timestamp") shouldBe fromBody.minus("timestamp")
        (fromQuery["amount"] as BigDecimal).toPlainString() shouldBe "162.00"
        fromQuery["currency"] shouldBe "BRL"
    }

    @Test
    fun `without a sector the response carries the total and the breakdown`() {
        val body = getJson("/revenue?date=2026-08-15")

        (body["amount"] as BigDecimal).toPlainString() shouldBe "175.53"
        body["currency"] shouldBe "BRL"
        body["date"] shouldBe "2026-08-15"

        val sectors = body["sectors"] as List<*>
        sectors.size shouldBe 2
        (sectors.first() as Map<*, *>)["sector"] shouldBe "A"
        ((sectors.first() as Map<*, *>)["amount"] as BigDecimal).toPlainString() shouldBe "162.00"
    }

    @Test
    fun `free sessions are exposed apart from the billed total`() {
        val body = getJson("/revenue?date=2026-08-15&sector=B")

        (body["amount"] as BigDecimal).toPlainString() shouldBe "13.53"
        body["free_sessions_count"] shouldBe 1
        body["sessions"] shouldBe 2
    }

    @Test
    fun `an unknown sector answers 404`() {
        get("/revenue?date=2026-08-15&sector=Z").status shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `an invalid date answers 400`() {
        get("/revenue?date=15-08-2026").status shouldBe HttpStatus.BAD_REQUEST
        get("/revenue?date=not-a-date").status shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `an exit at 21 local time does not leak into the next day in utc`() {
        val revenue = server.applicationContext.getBean(RevenueRepository::class.java)
        val lateEvening = Instant.parse("2026-08-17T00:00:00Z")
        val clock = server.applicationContext.getBean(com.teslapark.domain.port.ClockProvider::class.java)

        val localDate = clock.localDateOf(lateEvening)
        localDate shouldBe LocalDate.parse("2026-08-16")

        revenue.accumulate(RevenueEntry(SectorCode("A"), localDate, Money.of("40.50")))

        (getJson("/revenue?date=2026-08-16&sector=A")["amount"] as BigDecimal).toPlainString() shouldBe "40.50"
        (getJson("/revenue?date=2026-08-17&sector=A")["amount"] as BigDecimal).toPlainString() shouldBe "0.00"
    }

    @Test
    fun `the snapshot matches the sum of the sessions after a thousand concurrent exits`() {
        val revenue = server.applicationContext.getBean(RevenueRepository::class.java)
        val sessions = server.applicationContext.getBean(ParkingSessionRepository::class.java)
        val reconcile = server.applicationContext.getBean(ReconcileDailyRevenue::class.java)
        val reconciliationDate = LocalDate.parse("2026-09-01")

        val plateSequence = AtomicInteger()
        val pool = Executors.newFixedThreadPool(CONCURRENCY)
        val start = CountDownLatch(1)
        val done = CountDownLatch(CONCURRENT_EXITS)

        repeat(CONCURRENT_EXITS) {
            pool.submit {
                start.await()
                runCatching {
                    val plate = LicensePlate("RC%05d".format(plateSequence.incrementAndGet()))
                    val opened =
                        sessions
                            .save(
                                ParkingSession.enter(
                                    licensePlate = plate,
                                    entryTime = Instant.parse("2026-09-01T10:00:00Z"),
                                    occupancyRateAtEntry = BigDecimal("0.5000"),
                                    priceMultiplier = BigDecimal("1.100"),
                                ),
                            ).valueOrNull()!!

                    val charged =
                        opened
                            .exit(Instant.parse("2026-09-01T12:00:00Z"))
                            .valueOrNull()!!
                            .copy(sectorCode = SectorCode("B"))
                            .withCharge(Money.of("4.10"), 2, Money.of("9.02"), reconciliationDate)

                    sessions.save(charged)
                    revenue.accumulate(RevenueEntry(SectorCode("B"), reconciliationDate, Money.of("9.02")))
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await(RECONCILIATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pool.shutdownNow()

        val snapshot = revenue.findBy(SectorCode("B"), reconciliationDate)!!
        snapshot.sessionsCount shouldBe CONCURRENT_EXITS
        snapshot.total shouldBe Money.of("9.02") * CONCURRENT_EXITS
        sessions.sumChargedOn(reconciliationDate)[SectorCode("B")] shouldBe snapshot.total
        reconcile.execute(reconciliationDate).isBalanced shouldBe true
    }

    private val exactMapper =
        com.fasterxml.jackson.databind
            .ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

    private companion object {
        const val HTTP_OK = 200
        const val SYNC_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 250L
        const val CONCURRENT_EXITS = 1_000
        const val CONCURRENCY = 16
        const val RECONCILIATION_TIMEOUT_SECONDS = 180L

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
