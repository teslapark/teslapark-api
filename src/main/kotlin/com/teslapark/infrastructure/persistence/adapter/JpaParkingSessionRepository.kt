package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.CurrencyCode
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.ParkingSessionRepository
import com.teslapark.infrastructure.persistence.entity.ParkingSessionEntity
import com.teslapark.infrastructure.persistence.jpa.ParkingSessionJpaRepository
import com.teslapark.infrastructure.persistence.jpa.SectorJpaRepository
import com.teslapark.infrastructure.persistence.jpa.SpotJpaRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate

@Singleton
open class JpaParkingSessionRepository(
    private val sessions: ParkingSessionJpaRepository,
    private val sectors: SectorJpaRepository,
    private val spots: SpotJpaRepository,
    private val vehicles: VehicleRegistry,
    private val entityManager: EntityManager,
) : ParkingSessionRepository {
    @Transactional
    override fun findActiveSessionFor(licensePlate: LicensePlate): ParkingSession? =
        sessions.findByActivePlate(licensePlate.value)?.toSession()

    @Transactional
    override fun findById(id: Long): ParkingSession? = sessions.findById(id).orElse(null)?.toSession()

    @Transactional
    override fun save(session: ParkingSession): DomainResult<ParkingSession> =
        if (session.id == null) insert(session) else update(session).asSuccess()

    @Transactional
    override fun countOpenSessions(): Int = sessions.countOpenSessions().toInt()

    @Transactional
    override fun sumChargedOn(revenueDate: LocalDate): Map<SectorCode, Money> =
        entityManager
            .createNativeQuery(SUM_CHARGED_BY_SECTOR)
            .setParameter("revenueDate", revenueDate)
            .resultList
            .filterIsInstance<Array<*>>()
            .associate { row -> SectorCode(row[0] as String) to Money.of(row[1] as BigDecimal) }

    private fun insert(session: ParkingSession): DomainResult<ParkingSession> {
        val inserted =
            sessions.insertIfPlateIsFree(
                vehicleId = vehicles.ensureVehicle(session.licensePlate),
                licensePlate = session.licensePlate.value,
                status = session.status.name,
                entryTime = session.entryTime,
                occupancyRate = session.occupancyRateAtEntry,
                priceMultiplier = session.priceMultiplier,
                currency = session.basePriceApplied?.currency?.code ?: CurrencyCode.BRL.code,
            )

        if (inserted == 0) return DomainError.SessionAlreadyOpen(session.licensePlate.value).asFailure()

        val stored = sessions.findByActivePlate(session.licensePlate.value)!!
        applyMutableState(stored, session)
        sessions.update(stored)
        return session.copy(id = stored.id).asSuccess()
    }

    private fun update(session: ParkingSession): ParkingSession {
        val entity = sessions.findById(session.id!!).orElseThrow()
        entity.status = session.status.name
        applyMutableState(entity, session)
        entity.version += 1
        sessions.update(entity)
        return session
    }

    private fun applyMutableState(
        entity: ParkingSessionEntity,
        session: ParkingSession,
    ) {
        session.sectorCode?.let { code -> entity.sectorId = sectors.findByCode(code.value)?.id ?: entity.sectorId }
        session.spotExternalId?.let { external -> entity.spotId = spots.findByExternalId(external)?.id ?: entity.spotId }
        entity.parkedTime = session.parkedTime ?: entity.parkedTime
        entity.exitTime = session.exitTime ?: entity.exitTime
        entity.durationMinutes = session.stay?.let(Duration::toMinutes)?.toInt() ?: entity.durationMinutes
        entity.basePriceApplied = session.basePriceApplied?.amount ?: entity.basePriceApplied
        entity.billedHours = session.billedHours ?: entity.billedHours
        entity.amountCharged = session.amountCharged?.amount ?: entity.amountCharged
        entity.revenueDate = session.revenueDate ?: entity.revenueDate
    }

    private fun ParkingSessionEntity.toSession(): ParkingSession =
        toDomain(
            sectorCode = sectorId?.let { sectors.findById(it).orElse(null)?.code },
            spotExternalId = spotId?.let { spots.findById(it).orElse(null)?.externalId },
        )

    private companion object {
        const val SUM_CHARGED_BY_SECTOR = """
            SELECT sec.code, COALESCE(SUM(ps.amount_charged), 0)
            FROM parking_session ps
            JOIN sector sec ON sec.id = ps.sector_id
            WHERE ps.revenue_date = :revenueDate AND ps.status = 'EXITED'
            GROUP BY sec.code
        """
    }
}
