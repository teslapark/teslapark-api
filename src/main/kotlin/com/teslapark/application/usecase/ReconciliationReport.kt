package com.teslapark.application.usecase

import java.time.LocalDate

data class ReconciliationReport(
    val revenueDate: LocalDate,
    val discrepancies: List<RevenueDiscrepancy>,
) {
    val isBalanced: Boolean get() = discrepancies.isEmpty()
}
