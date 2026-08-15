package com.teslapark.domain.model

import com.teslapark.domain.event.GateEventType
import java.time.Instant

@JvmInline
value class IdempotencyKey private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val LENGTH = 64

        operator fun invoke(raw: String): IdempotencyKey {
            require(raw.length == LENGTH) { "idempotency key must be a $LENGTH character digest" }
            return IdempotencyKey(raw)
        }
    }
}

enum class ProcessingStatus {
    RECEIVED,
    PROCESSED,
    DUPLICATE,
    FAILED,
}

data class WebhookEventRecord(
    val idempotencyKey: IdempotencyKey,
    val eventType: GateEventType,
    val receivedAt: Instant,
    val rawPayload: String,
    val licensePlate: LicensePlate? = null,
    val eventTime: Instant? = null,
    val sessionId: Long? = null,
    val processedAt: Instant? = null,
    val status: ProcessingStatus = ProcessingStatus.RECEIVED,
    val id: Long? = null,
) {
    fun markProcessed(
        at: Instant,
        sessionId: Long?,
    ): WebhookEventRecord = copy(status = ProcessingStatus.PROCESSED, processedAt = at, sessionId = sessionId ?: this.sessionId)

    fun markFailed(at: Instant): WebhookEventRecord = copy(status = ProcessingStatus.FAILED, processedAt = at)
}
