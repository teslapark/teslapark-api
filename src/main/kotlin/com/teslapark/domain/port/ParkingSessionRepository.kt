package com.teslapark.domain.port

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.ParkingSession

interface ParkingSessionRepository {
    fun findActiveSessionFor(licensePlate: LicensePlate): ParkingSession?

    fun findById(id: Long): ParkingSession?

    fun save(session: ParkingSession): DomainResult<ParkingSession>

    fun countOpenSessions(): Int
}
