package com.teslapark.infrastructure.persistence.jpa

import com.teslapark.infrastructure.persistence.entity.WebhookEventEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.time.Instant

@Repository
interface WebhookEventJpaRepository : CrudRepository<WebhookEventEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): WebhookEventEntity?

    fun deleteByIdempotencyKey(idempotencyKey: String): Long

    @Query(
        value = """
            INSERT IGNORE INTO webhook_event
                (idempotency_key, event_type, license_plate, event_time, received_at, processing_status, raw_payload)
            VALUES (:idempotencyKey, :eventType, :licensePlate, :eventTime, :receivedAt, :processingStatus, :rawPayload)
        """,
        nativeQuery = true,
    )
    @Suppress("LongParameterList")
    fun insertIfAbsent(
        idempotencyKey: String,
        eventType: String,
        licensePlate: String?,
        eventTime: Instant?,
        receivedAt: Instant,
        processingStatus: String,
        rawPayload: String,
    ): Int
}
