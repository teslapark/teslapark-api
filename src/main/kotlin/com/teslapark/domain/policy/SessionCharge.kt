package com.teslapark.domain.policy

import com.teslapark.domain.model.Money

data class SessionCharge(
    val chargeableHours: Int,
    val amount: Money,
) {
    val isWithinFreeWindow: Boolean get() = chargeableHours == 0
}
