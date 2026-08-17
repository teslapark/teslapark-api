package com.teslapark.domain.port

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.SectorCode
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

class InMemoryParkingSessionRepository : ParkingSessionRepository {
    private val sessions = linkedMapOf<Long, ParkingSession>()
    private val sequence = AtomicLong()

    override fun findActiveSessionFor(licensePlate: LicensePlate): ParkingSession? =
        sessions.values.firstOrNull { it.licensePlate == licensePlate && !it.isClosed }

    override fun findById(id: Long): ParkingSession? = sessions[id]

    override fun save(session: ParkingSession): DomainResult<ParkingSession> {
        if (session.id == null) {
            val alreadyOpen = findActiveSessionFor(session.licensePlate)
            if (alreadyOpen != null) return DomainError.SessionAlreadyOpen(session.licensePlate.value).asFailure()

            val persisted = session.copy(id = sequence.incrementAndGet())
            sessions[persisted.id!!] = persisted
            return persisted.asSuccess()
        }

        sessions[session.id] = session
        return session.asSuccess()
    }

    override fun countOpenSessions(): Int = sessions.values.count { !it.isClosed }

    override fun sumChargedOn(revenueDate: LocalDate): Map<SectorCode, Money> =
        sessions.values
            .filter { it.revenueDate == revenueDate && it.amountCharged != null && it.sectorCode != null }
            .groupBy { it.sectorCode!! }
            .mapValues { (_, group) -> group.map { it.amountCharged!! }.reduce(Money::plus) }
}
