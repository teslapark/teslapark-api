package com.teslapark.application.usecase

import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.IdempotencyKey
import jakarta.inject.Singleton
import java.security.MessageDigest

@Singleton
class EventIdempotencyGuard {
    fun keyFor(event: GateEvent): IdempotencyKey =
        IdempotencyKey(digestOf("${event.type.name}$SEPARATOR${event.licensePlate.value}$SEPARATOR${discriminatorOf(event)}"))

    private fun discriminatorOf(event: GateEvent): String =
        when (event) {
            is GateEvent.EntryEvent -> event.entryTime.toString()
            is GateEvent.ExitEvent -> event.exitTime.toString()
            is GateEvent.ParkedEvent -> event.coordinates.toString()
        }

    private fun digestOf(source: String): String =
        MessageDigest
            .getInstance(ALGORITHM)
            .digest(source.toByteArray())
            .joinToString(separator = "") { byte -> HEX_FORMAT.format(byte) }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val SEPARATOR = "|"
        const val HEX_FORMAT = "%02x"
    }
}
