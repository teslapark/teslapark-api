package com.teslapark.domain.port

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.SectorCode
import java.time.LocalDate

interface ParkingSessionRepository {
    fun findActiveSessionFor(licensePlate: LicensePlate): ParkingSession?

    fun findById(id: Long): ParkingSession?

    fun save(session: ParkingSession): DomainResult<ParkingSession>

    fun countOpenSessions(): Int

    fun sumChargedOn(revenueDate: LocalDate): Map<SectorCode, Money>
}
