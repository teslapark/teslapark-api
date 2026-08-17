package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.SectorRepository
import com.teslapark.infrastructure.persistence.entity.SectorEntity
import com.teslapark.infrastructure.persistence.jpa.SectorJpaRepository
import com.teslapark.infrastructure.persistence.mapper.toDomain
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class JpaSectorRepository(
    private val sectors: SectorJpaRepository,
    private val garageRegistry: GarageRegistry,
) : SectorRepository {
    @Transactional
    override fun findByCode(code: SectorCode): Sector? = sectors.findByCode(code.value)?.toDomain()

    @Transactional
    override fun findAll(): List<Sector> = sectors.listOrderByCode().map { it.toDomain() }

    @Transactional
    override fun synchronize(sectors: List<Sector>): List<Sector> {
        val garageId = garageRegistry.ensureGarage()
        sectors.forEach { sector -> upsert(garageId, sector) }
        return findAll()
    }

    @Transactional
    override fun totalCapacity(): Int = sectors.totalCapacity().toInt()

    private fun upsert(
        garageId: Long,
        sector: Sector,
    ) {
        val entity = sectors.findByCode(sector.code.value) ?: SectorEntity()
        entity.garageId = garageId
        entity.code = sector.code.value
        entity.basePrice = sector.basePrice.amount
        entity.maxCapacity = sector.maxCapacity
        entity.openHour = sector.openHour
        entity.closeHour = sector.closeHour
        entity.durationLimitMinutes = sector.durationLimitMinutes.toInt()
        if (entity.id == null) sectors.save(entity) else sectors.update(entity)
    }
}
