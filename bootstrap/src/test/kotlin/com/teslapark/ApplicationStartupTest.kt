package com.teslapark

import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationStartupTest {
    private lateinit var server: EmbeddedServer
    private lateinit var httpClient: HttpClient
    private lateinit var client: BlockingHttpClient

    @BeforeAll
    fun startServer() {
        server = ApplicationContext.run(EmbeddedServer::class.java, "test")
        httpClient = HttpClient.create(server.url)
        client = httpClient.toBlocking()
    }

    @AfterAll
    fun stopServer() {
        httpClient.close()
        server.close()
    }

    @Test
    fun `application context starts`() {
        server.isRunning shouldBe true
    }

    @Test
    fun `health endpoint reports the service as up`() {
        val response = client.exchange(HttpRequest.GET<Any>("/health"), Map::class.java)

        response.status shouldBe HttpStatus.OK
        response.body()["status"] shouldBe "UP"
    }
}
