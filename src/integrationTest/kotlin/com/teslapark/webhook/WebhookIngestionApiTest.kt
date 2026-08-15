package com.teslapark.webhook

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.teslapark.MySqlSupport
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.AnomalyRepository
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
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebhookIngestionApiTest {
    private lateinit var gateControlSystem: HttpServer
    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    @BeforeAll
    fun startStack() {
        gateControlSystem = HttpServer.create(InetSocketAddress(0), 0)
        gateControlSystem.createContext("/garage", ::respondWithGarage)
        gateControlSystem.start()

        val jdbcUrl = MySqlSupport.createIsolatedDatabase("webhook_ingestion_test")
        server =
            ApplicationContext.run(
                EmbeddedServer::class.java,
                MySqlSupport.datasourceProperties(jdbcUrl) +
                    mapOf(
                        "micronaut.http.services.gate-control-system.url" to
                            "http://localhost:${gateControlSystem.address.port}",
                        "teslapark.garage.sync.retry-delay" to "1s",
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
            if (post("""{"license_plate":"AAA0000","event_type":"EXIT","exit_time":"$EXIT_TIME"}""").status
                != HttpStatus.SERVICE_UNAVAILABLE
            ) {
                return
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    private fun post(payload: String): HttpResponse<*> =
        try {
            client.toBlocking().exchange(HttpRequest.POST("/webhook", payload), Map::class.java)
        } catch (rejected: HttpClientResponseException) {
            rejected.response
        }

    private fun bodyOf(response: HttpResponse<*>): Map<*, *> = response.getBody(Map::class.java).orElse(emptyMap<Any, Any>())

    private fun entry(plate: String) = """{"license_plate":"$plate","entry_time":"$ENTRY_TIME","event_type":"ENTRY"}"""

    private fun parked(
        plate: String,
        latitude: String,
        longitude: String,
    ) = """{"license_plate":"$plate","lat":$latitude,"lng":$longitude,"event_type":"PARKED"}"""

    private fun exit(plate: String) = """{"license_plate":"$plate","exit_time":"$EXIT_TIME","event_type":"EXIT"}"""

    @Test
    fun `the full flow over http bills the session and credits the sector revenue`() {
        val plate = "ZUL0100"

        bodyOf(post(entry(plate)))["status"] shouldBe "ACCEPTED"
        bodyOf(post(parked(plate, "-23.561601", "-46.655901")))["status"] shouldBe "ACCEPTED"

        val exited = bodyOf(post(exit(plate)))
        exited["status"] shouldBe "ACCEPTED"
        exited["sector"] shouldBe "A"
        exited["billed_hours"] shouldBe 3
        exited["duration_minutes"] shouldBe 130
        exited["currency"] shouldBe "BRL"

        val revenue = server.applicationContext.getBean(RevenueRepository::class.java)
        revenue.findBy(SectorCode("A"), REVENUE_DATE)!!.sessionsCount shouldBe 1
    }

    @Test
    fun `an exit without entry answers 200 with an anomaly and no revenue`() {
        val anomalies = server.applicationContext.getBean(AnomalyRepository::class.java)
        val before = anomalies.countOfType(AnomalyType.EXIT_WITHOUT_ENTRY)

        val response = post(exit("ZUL9999"))

        response.status shouldBe HttpStatus.OK
        bodyOf(response)["status"] shouldBe "IGNORED"
        bodyOf(response)["anomaly"] shouldBe AnomalyType.EXIT_WITHOUT_ENTRY.name
        anomalies.countOfType(AnomalyType.EXIT_WITHOUT_ENTRY) shouldBe before + 1
    }

    @Test
    fun `the same entry sent twenty times in parallel creates one session and nineteen duplicates`() {
        val payload = entry("ZUL0200")
        val statuses = ConcurrentLinkedQueue<String>()

        runConcurrently(PARALLEL_SENDERS) { statuses += bodyOf(post(payload))["status"].toString() }

        statuses.count { it == "ACCEPTED" } shouldBe 1
        statuses.count { it == "DUPLICATE" } shouldBe PARALLEL_SENDERS - 1
    }

    @Test
    fun `a duplicated exit credits the revenue exactly once`() {
        val plate = "ZUL0300"
        val revenue = server.applicationContext.getBean(RevenueRepository::class.java)

        post(entry(plate))
        post(parked(plate, "-23.561611", "-46.655911"))

        val snapshotBefore = revenue.findBy(SectorCode("B"), REVENUE_DATE)
        val totalBefore = snapshotBefore?.total ?: Money.zero()
        val sessionsBefore = snapshotBefore?.sessionsCount ?: 0

        val bodies = ConcurrentLinkedQueue<Map<*, *>>()
        runConcurrently(PARALLEL_EXITS) { bodies += bodyOf(post(exit(plate))) }

        val accepted = bodies.filter { it["status"] == "ACCEPTED" }
        accepted.size shouldBe 1
        bodies.count { it["status"] == "DUPLICATE" } shouldBe PARALLEL_EXITS - 1

        val billed = Money.of(accepted.single()["amount"].toString())
        val after = revenue.findBy(SectorCode("B"), REVENUE_DATE)!!
        after.total shouldBe totalBefore + billed
        after.sessionsCount shouldBe sessionsBefore + 1
    }

    @Test
    fun `a parked event on an unknown coordinate answers 200 and keeps the session entered`() {
        val plate = "ZUL0400"
        post(entry(plate))

        val response = post(parked(plate, "-23.999999", "-46.999999"))

        response.status shouldBe HttpStatus.OK
        bodyOf(response)["anomaly"] shouldBe AnomalyType.PARKED_UNKNOWN_SPOT.name
        bodyOf(post(exit(plate)))["status"] shouldBe "ACCEPTED"
    }

    @Test
    fun `an exit earlier than the entry answers 422`() {
        val plate = "ZUL0500"
        post(entry(plate))

        val response = post("""{"license_plate":"$plate","exit_time":"2026-08-15T11:00:00Z","event_type":"EXIT"}""")

        response.status shouldBe HttpStatus.UNPROCESSABLE_ENTITY
        post(exit(plate))
    }

    @Test
    fun `a malformed payload answers 400 and never 5xx`() {
        post("""{"event_type":"ENTRY"}""").status shouldBe HttpStatus.BAD_REQUEST
        post("""not json at all""").status shouldBe HttpStatus.BAD_REQUEST
        post("""{"license_plate":"ZUL0600","event_type":"UNKNOWN"}""").status shouldBe HttpStatus.BAD_REQUEST
    }

    private fun runConcurrently(
        threads: Int,
        action: () -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) {
            pool.submit {
                start.await()
                runCatching(action)
                done.countDown()
            }
        }
        start.countDown()
        done.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pool.shutdownNow()
    }

    private companion object {
        const val HTTP_OK = 200
        const val PARALLEL_SENDERS = 20
        const val PARALLEL_EXITS = 10
        const val SYNC_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 250L
        const val CONCURRENCY_TIMEOUT_SECONDS = 60L

        const val ENTRY_TIME = "2026-08-15T12:00:00Z"
        const val EXIT_TIME = "2026-08-15T14:10:00Z"
        val REVENUE_DATE: LocalDate = LocalDate.parse("2026-08-15")

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
