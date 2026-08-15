package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.port.TransactionBoundary
import jakarta.inject.Singleton

@Singleton
class JdbcTransactionBoundary(
    private val jdbc: JdbcOperations,
) : TransactionBoundary {
    override fun <T> inTransaction(block: () -> T): T = jdbc.inTransaction { block() }
}
