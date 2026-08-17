package com.teslapark.domain.port

import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.SessionAnomaly
import java.util.concurrent.atomic.AtomicLong

class InMemoryAnomalyRepository : AnomalyRepository {
    private val anomalies = mutableListOf<SessionAnomaly>()
    private val sequence = AtomicLong()

    override fun record(anomaly: SessionAnomaly): SessionAnomaly {
        val persisted = anomaly.copy(id = sequence.incrementAndGet())
        anomalies += persisted
        return persisted
    }

    override fun findAllOfType(type: AnomalyType): List<SessionAnomaly> = anomalies.filter { it.type == type }

    override fun countOfType(type: AnomalyType): Int = findAllOfType(type).size
}
