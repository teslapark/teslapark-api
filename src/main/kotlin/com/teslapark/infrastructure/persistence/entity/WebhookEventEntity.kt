package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "webhook_event")
class WebhookEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "idempotency_key", nullable = false)
    lateinit var idempotencyKey: String

    @Column(name = "event_type", nullable = false)
    lateinit var eventType: String

    @Column(name = "license_plate")
    var licensePlate: String? = null

    @Column(name = "session_id")
    var sessionId: Long? = null

    @Column(name = "event_time")
    var eventTime: Instant? = null

    @Column(name = "received_at", nullable = false)
    lateinit var receivedAt: Instant

    @Column(name = "processed_at")
    var processedAt: Instant? = null

    @Column(name = "processing_status", nullable = false)
    lateinit var processingStatus: String

    @Column(name = "raw_payload", nullable = false)
    lateinit var rawPayload: String
}
