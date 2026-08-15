package com.teslapark.domain.event

import com.teslapark.domain.model.Coordinates
import com.teslapark.domain.model.LicensePlate
import java.time.Instant

sealed interface GateEvent {
    val licensePlate: LicensePlate

    data class EntryEvent(
        override val licensePlate: LicensePlate,
        val entryTime: Instant,
    ) : GateEvent

    data class ParkedEvent(
        override val licensePlate: LicensePlate,
        val coordinates: Coordinates,
    ) : GateEvent

    data class ExitEvent(
        override val licensePlate: LicensePlate,
        val exitTime: Instant,
    ) : GateEvent
}
