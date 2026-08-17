package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.event.EventResult
import com.teslapark.domain.event.GateEvent
import com.teslapark.domain.model.ProcessingStatus
import com.teslapark.domain.model.WebhookEventRecord
import com.teslapark.domain.port.ClockProvider
import com.teslapark.domain.port.MetricsPublisher
import com.teslapark.domain.port.TransactionBoundary
import com.teslapark.domain.port.WebhookEventRepository
import jakarta.inject.Singleton

@Singleton
class ProcessGateEvent(
    private val idempotencyGuard: EventIdempotencyGuard,
    private val webhookEvents: WebhookEventRepository,
    private val handleEntry: HandleEntryEvent,
    private val handleParked: HandleParkedEvent,
    private val handleExit: HandleExitEvent,
    private val transaction: TransactionBoundary,
    private val metrics: MetricsPublisher,
    private val clock: ClockProvider,
) {
    fun execute(
        event: GateEvent,
        rawPayload: String,
    ): GateEventOutcome {
        val key = idempotencyGuard.keyFor(event)
        val record =
            WebhookEventRecord(
                idempotencyKey = key,
                eventType = event.type,
                receivedAt = clock.now(),
                rawPayload = rawPayload,
                licensePlate = event.licensePlate,
                eventTime = eventTimeOf(event),
            )

        val registered =
            when (val outcome = webhookEvents.registerIfAbsent(record)) {
                is DomainResult.Failure -> {
                    metrics.webhookEventReceived(event.type, EventResult.DUPLICATE)
                    return GateEventOutcome.Duplicate(webhookEvents.findBy(key)?.sessionId)
                }
                is DomainResult.Success -> outcome.value
            }

        val result = transaction.inTransaction { dispatch(event) }
        metrics.webhookEventReceived(event.type, resultOf(result))

        if (result is GateEventOutcome.Rejected) {
            webhookEvents.discard(key)
            return result
        }

        webhookEvents.save(
            registered
                .markProcessed(clock.now(), sessionIdOf(result))
                .copy(status = processingStatusOf(result)),
        )

        return result
    }

    private fun dispatch(event: GateEvent): GateEventOutcome =
        when (event) {
            is GateEvent.EntryEvent -> handleEntry.execute(event)
            is GateEvent.ParkedEvent -> handleParked.execute(event)
            is GateEvent.ExitEvent -> handleExit.execute(event)
        }

    private fun eventTimeOf(event: GateEvent) =
        when (event) {
            is GateEvent.EntryEvent -> event.entryTime
            is GateEvent.ExitEvent -> event.exitTime
            is GateEvent.ParkedEvent -> null
        }

    private fun sessionIdOf(outcome: GateEventOutcome): Long? =
        when (outcome) {
            is GateEventOutcome.Accepted -> outcome.session.id
            is GateEventOutcome.Duplicate -> outcome.sessionId
            else -> null
        }

    private fun resultOf(outcome: GateEventOutcome): EventResult =
        when (outcome) {
            is GateEventOutcome.Accepted -> EventResult.PROCESSED
            is GateEventOutcome.Duplicate -> EventResult.DUPLICATE
            is GateEventOutcome.Ignored -> EventResult.IGNORED
            is GateEventOutcome.Rejected -> EventResult.REJECTED
        }

    private fun processingStatusOf(outcome: GateEventOutcome): ProcessingStatus =
        when (outcome) {
            is GateEventOutcome.Accepted -> ProcessingStatus.PROCESSED
            is GateEventOutcome.Duplicate -> ProcessingStatus.DUPLICATE
            is GateEventOutcome.Ignored -> ProcessingStatus.PROCESSED
            is GateEventOutcome.Rejected -> ProcessingStatus.FAILED
        }
}
