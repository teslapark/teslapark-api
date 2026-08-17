package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.infrastructure.config.GarageConfigurationProperties
import com.teslapark.infrastructure.persistence.jpa.GarageJpaRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class GarageRegistry(
    private val garages: GarageJpaRepository,
    private val configuration: GarageConfigurationProperties,
) {
    @Transactional
    open fun ensureGarage(): Long {
        garages.upsert(configuration.name, configuration.timezone, configuration.currency)
        return garages.findByName(configuration.name)!!.id!!
    }
}
