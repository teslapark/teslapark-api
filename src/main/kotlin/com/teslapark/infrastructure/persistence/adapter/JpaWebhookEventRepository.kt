package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.WebhookEventRecord
import com.teslapark.domain.port.WebhookEventRepository
import com.teslapark.infrastructure.persistence.jpa.WebhookEventJpaRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class JpaWebhookEventRepository(
    private val events: WebhookEventJpaRepository,
) : WebhookEventRepository {
    @Transactional
    override fun registerIfAbsent(event: WebhookEventRecord): DomainResult<WebhookEventRecord> {
        val inserted =
            events.insertIfAbsent(
                idempotencyKey = event.idempotencyKey.value,
                eventType = event.eventType.name,
                licensePlate = event.licensePlate?.value,
                eventTime = event.eventTime,
                receivedAt = event.receivedAt,
                processingStatus = event.status.name,
                rawPayload = event.rawPayload,
            )

        if (inserted == 0) return DomainError.DuplicateWebhookEvent(event.idempotencyKey.value).asFailure()

        return event.copy(id = events.findByIdempotencyKey(event.idempotencyKey.value)?.id).asSuccess()
    }

    @Transactional
    override fun findBy(idempotencyKey: IdempotencyKey): WebhookEventRecord? = events.findByIdempotencyKey(idempotencyKey.value)?.toDomain()

    @Transactional
    override fun save(event: WebhookEventRecord): WebhookEventRecord {
        val entity = events.findByIdempotencyKey(event.idempotencyKey.value) ?: return event
        entity.processingStatus = event.status.name
        entity.processedAt = event.processedAt
        entity.sessionId = event.sessionId
        events.update(entity)
        return event
    }

    @Transactional
    override fun discard(idempotencyKey: IdempotencyKey) {
        events.deleteByIdempotencyKey(idempotencyKey.value)
    }
}
