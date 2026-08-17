package com.teslapark.domain.port

import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.SessionAnomaly

interface AnomalyRepository {
    fun record(anomaly: SessionAnomaly): SessionAnomaly

    fun findAllOfType(type: AnomalyType): List<SessionAnomaly>

    fun countOfType(type: AnomalyType): Int
}
