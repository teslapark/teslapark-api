package com.teslapark.domain.event

import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.LicensePlate
import java.time.Instant

sealed interface GateEvent {
    val licensePlate: LicensePlate
    val type: GateEventType

    data class EntryEvent(
        override val licensePlate: LicensePlate,
        val entryTime: Instant,
    ) : GateEvent {
        override val type: GateEventType get() = GateEventType.ENTRY
    }

    data class ParkedEvent(
        override val licensePlate: LicensePlate,
        val coordinates: Coordinates,
    ) : GateEvent {
        override val type: GateEventType get() = GateEventType.PARKED
    }

    data class ExitEvent(
        override val licensePlate: LicensePlate,
        val exitTime: Instant,
    ) : GateEvent {
        override val type: GateEventType get() = GateEventType.EXIT
    }
}
