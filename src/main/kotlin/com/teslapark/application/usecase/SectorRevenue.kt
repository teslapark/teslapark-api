package com.teslapark.application.usecase

import com.teslapark.domain.model.Money
import com.teslapark.domain.model.SectorCode

data class SectorRevenue(
    val sector: SectorCode,
    val amount: Money,
    val sessions: Int,
    val freeSessions: Int,
)
