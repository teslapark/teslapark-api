package com.teslapark.application.usecase

import com.teslapark.domain.model.Money
import com.teslapark.domain.model.SectorCode

data class RevenueDiscrepancy(
    val sector: SectorCode,
    val snapshotTotal: Money,
    val sessionsTotal: Money,
)
