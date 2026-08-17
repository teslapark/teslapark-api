package com.teslapark.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "parking_spot")
class SpotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "external_id", nullable = false)
    var externalId: Long = 0

    @Column(name = "sector_id", nullable = false)
    var sectorId: Long = 0

    @Column(name = "lat", nullable = false)
    lateinit var latitude: BigDecimal

    @Column(name = "lng", nullable = false)
    lateinit var longitude: BigDecimal

    @Column(name = "occupied", nullable = false)
    var occupied: Boolean = false

    @Column(name = "current_session_id")
    var currentSessionId: Long? = null
}
