package com.teslapark.domain.port

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.WebhookEventRecord
import java.util.concurrent.atomic.AtomicLong

class InMemoryWebhookEventRepository : WebhookEventRepository {
    private val events = linkedMapOf<IdempotencyKey, WebhookEventRecord>()
    private val sequence = AtomicLong()

    override fun registerIfAbsent(event: WebhookEventRecord): DomainResult<WebhookEventRecord> {
        events[event.idempotencyKey]?.let { return DomainError.DuplicateWebhookEvent(it.idempotencyKey.value).asFailure() }

        val persisted = event.copy(id = sequence.incrementAndGet())
        events[persisted.idempotencyKey] = persisted
        return persisted.asSuccess()
    }

    override fun findBy(idempotencyKey: IdempotencyKey): WebhookEventRecord? = events[idempotencyKey]

    override fun save(event: WebhookEventRecord): WebhookEventRecord {
        events[event.idempotencyKey] = event
        return event
    }

    override fun discard(idempotencyKey: IdempotencyKey) {
        events.remove(idempotencyKey)
    }
}
