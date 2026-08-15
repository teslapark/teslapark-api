package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.WebhookEventRecord
import com.teslapark.domain.port.WebhookEventRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import com.teslapark.infrastructure.persistence.mapper.toTimestamp
import com.teslapark.infrastructure.persistence.mapper.toWebhookEventEntity
import jakarta.inject.Singleton

private const val SELECT_EVENT =
    """
    SELECT id, idempotency_key, event_type, license_plate, session_id, event_time,
           received_at, processed_at, processing_status, raw_payload
    FROM webhook_event
    """

@Singleton
class MySqlWebhookEventRepository(
    private val jdbc: JdbcOperations,
) : WebhookEventRepository {
    override fun registerIfAbsent(event: WebhookEventRecord): DomainResult<WebhookEventRecord> =
        translateConstraintViolation(
            block = {
                jdbc.inTransaction { connection ->
                    val id =
                        connection.insertReturningId(
                            """
                            INSERT INTO webhook_event
                                (idempotency_key, event_type, license_plate, event_time, received_at, processing_status, raw_payload)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent(),
                            event.idempotencyKey.value,
                            event.eventType.name,
                            event.licensePlate?.value,
                            event.eventTime?.toTimestamp(),
                            event.receivedAt.toTimestamp(),
                            event.status.name,
                            event.rawPayload,
                        )
                    event.copy(id = id).asSuccess()
                }
            },
            onViolation = { DomainError.DuplicateWebhookEvent(event.idempotencyKey.value) },
        )

    override fun findBy(idempotencyKey: IdempotencyKey): WebhookEventRecord? =
        jdbc.readOnly { connection ->
            connection.queryFirst("$SELECT_EVENT WHERE idempotency_key = ?", idempotencyKey.value) {
                it.toWebhookEventEntity().toDomain()
            }
        }

    override fun save(event: WebhookEventRecord): WebhookEventRecord =
        jdbc.inTransaction { connection ->
            connection.update(
                """
                UPDATE webhook_event
                SET processing_status = ?, processed_at = ?, session_id = ?
                WHERE idempotency_key = ?
                """.trimIndent(),
                event.status.name,
                event.processedAt?.toTimestamp(),
                event.sessionId,
                event.idempotencyKey.value,
            )
            event
        }

    override fun discard(idempotencyKey: IdempotencyKey) {
        jdbc.inTransaction { connection ->
            connection.update("DELETE FROM webhook_event WHERE idempotency_key = ?", idempotencyKey.value)
        }
    }
}
