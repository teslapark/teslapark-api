package com.teslapark.infrastructure.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.Coordinates
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

class WebhookRequestTest {
    private val objectMapper = ObjectMapper()

    private fun eventOf(payload: String): DomainResult<GateEvent> =
        objectMapper.readValue(payload, WebhookRequest::class.java).toGateEvent()

    private fun acceptedEvent(payload: String): GateEvent = eventOf(payload).shouldBeInstanceOf<DomainResult.Success<GateEvent>>().value

    @Test
    fun `the entry payload emitted by the simulator carries no zone and is read as utc`() {
        val event =
            acceptedEvent("""{"license_plate":"Y37142","entry_time":"2026-08-17T06:51:25","event_type":"ENTRY"}""")
                .shouldBeInstanceOf<GateEvent.EntryEvent>()

        event.licensePlate.value shouldBe "Y37142"
        event.entryTime shouldBe Instant.parse("2026-08-17T06:51:25Z")
    }

    @Test
    fun `the exit payload emitted by the simulator carries no zone and is read as utc`() {
        val event =
            acceptedEvent("""{"license_plate":"Y37142","exit_time":"2026-08-17T08:10:00","event_type":"EXIT"}""")
                .shouldBeInstanceOf<GateEvent.ExitEvent>()

        event.exitTime shouldBe Instant.parse("2026-08-17T08:10:00Z")
    }

    @Test
    fun `an explicit zone is honoured instead of being assumed`() {
        val utc = acceptedEvent("""{"license_plate":"Y37142","entry_time":"2026-08-17T06:51:25Z","event_type":"ENTRY"}""")
        val offset =
            acceptedEvent("""{"license_plate":"Y37142","entry_time":"2026-08-17T03:51:25-03:00","event_type":"ENTRY"}""")

        utc.shouldBeInstanceOf<GateEvent.EntryEvent>().entryTime shouldBe Instant.parse("2026-08-17T06:51:25Z")
        offset.shouldBeInstanceOf<GateEvent.EntryEvent>().entryTime shouldBe Instant.parse("2026-08-17T06:51:25Z")
    }

    @Test
    fun `the parked payload resolves the coordinates`() {
        val event =
            acceptedEvent("""{"license_plate":"Y37142","lat":-23.561684,"lng":-46.655981,"event_type":"PARKED"}""")
                .shouldBeInstanceOf<GateEvent.ParkedEvent>()

        event.coordinates shouldBe Coordinates.of("-23.561684", "-46.655981")
    }

    @Test
    fun `an absent timestamp is reported as a missing field`() {
        val failure =
            eventOf("""{"license_plate":"Y37142","event_type":"ENTRY"}""")
                .shouldBeInstanceOf<DomainResult.Failure>()

        failure.error shouldBe DomainError.MissingEventField("entry_time")
    }

    @Test
    fun `a present but unreadable timestamp is reported as a malformed payload`() {
        val failure =
            eventOf("""{"license_plate":"Y37142","entry_time":"17-08-2026 06:51","event_type":"ENTRY"}""")
                .shouldBeInstanceOf<DomainResult.Failure>()

        failure.error.shouldBeInstanceOf<DomainError.MalformedEventPayload>()
    }
}
