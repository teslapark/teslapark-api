package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.model.LicensePlate
import com.teslapark.infrastructure.persistence.jpa.VehicleJpaRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class VehicleRegistry(
    private val vehicles: VehicleJpaRepository,
) {
    @Transactional
    open fun ensureVehicle(licensePlate: LicensePlate): Long {
        vehicles.touch(licensePlate.value)
        return vehicles.findByLicensePlate(licensePlate.value)!!.id!!
    }
}
