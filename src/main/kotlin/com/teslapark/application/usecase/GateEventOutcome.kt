package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.model.AnomalyType
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.policy.SessionCharge
import java.math.BigDecimal

sealed interface GateEventOutcome {
    data class Accepted(
        val session: ParkingSession,
        val occupancyRate: BigDecimal? = null,
        val charge: SessionCharge? = null,
    ) : GateEventOutcome

    data class Duplicate(
        val sessionId: Long?,
    ) : GateEventOutcome

    data class Ignored(
        val anomaly: AnomalyType,
        val detail: String,
    ) : GateEventOutcome

    data class Rejected(
        val error: DomainError,
    ) : GateEventOutcome
}
