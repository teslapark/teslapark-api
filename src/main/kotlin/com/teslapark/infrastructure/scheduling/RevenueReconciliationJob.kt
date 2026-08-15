package com.teslapark.infrastructure.scheduling

import com.teslapark.application.usecase.ReconcileDailyRevenue
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
@Requires(property = "teslapark.revenue.reconciliation.enabled", notEquals = "false")
class RevenueReconciliationJob(
    private val reconcileDailyRevenue: ReconcileDailyRevenue,
) {
    private val logger = LoggerFactory.getLogger(RevenueReconciliationJob::class.java)

    @Scheduled(
        fixedDelay = "\${teslapark.revenue.reconciliation.interval:15m}",
        initialDelay = "\${teslapark.revenue.reconciliation.interval:15m}",
    )
    fun reconcileToday() {
        val report = runCatching { reconcileDailyRevenue.execute() }.getOrNull() ?: return

        if (report.isBalanced) {
            logger.debug("revenue snapshot reconciled for {}", report.revenueDate)
            return
        }

        report.discrepancies.forEach { discrepancy ->
            logger.warn(
                "revenue mismatch on {} sector {}: snapshot {} sessions {}",
                report.revenueDate,
                discrepancy.sector.value,
                discrepancy.snapshotTotal,
                discrepancy.sessionsTotal,
            )
        }
    }
}
