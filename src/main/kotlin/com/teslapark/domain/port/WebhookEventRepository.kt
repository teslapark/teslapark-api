package com.teslapark.domain.port

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.IdempotencyKey
import com.teslapark.domain.model.WebhookEventRecord

interface WebhookEventRepository {
    fun registerIfAbsent(event: WebhookEventRecord): DomainResult<WebhookEventRecord>

    fun findBy(idempotencyKey: IdempotencyKey): WebhookEventRecord?

    fun save(event: WebhookEventRecord): WebhookEventRecord
}
