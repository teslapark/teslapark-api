package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "session_anomaly")
class SessionAnomalyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "session_id")
    var sessionId: Long? = null

    @Column(name = "webhook_event_id")
    var webhookEventId: Long? = null

    @Column(name = "anomaly_type", nullable = false)
    lateinit var anomalyType: String

    @Column(name = "description")
    var description: String? = null

    @Column(name = "detected_at", nullable = false)
    lateinit var detectedAt: Instant

    @Column(name = "resolved", nullable = false)
    var resolved: Boolean = false
}
