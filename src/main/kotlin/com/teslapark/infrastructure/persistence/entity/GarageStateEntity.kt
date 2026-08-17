package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "garage_state")
class GarageStateEntity {
    @Id
    @Column(name = "garage_id")
    var garageId: Long = 0

    @Column(name = "total_capacity", nullable = false)
    var totalCapacity: Int = 0

    @Column(name = "occupied_spots", nullable = false)
    var occupiedSpots: Int = 0

    @Column(name = "occupancy_rate", nullable = false)
    var occupancyRate: BigDecimal = BigDecimal.ZERO

    @Column(name = "closed_by_capacity", nullable = false)
    var closedByCapacity: Boolean = false

    @Column(name = "last_sync_at")
    var lastSyncAt: Instant? = null

    @Column(name = "config_status", nullable = false)
    lateinit var configStatus: String

    @Column(name = "version", nullable = false)
    var version: Long = 0
}
