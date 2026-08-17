package com.teslapark.infrastructure.persistence.adapter

import com.teslapark.domain.port.TransactionBoundary
import jakarta.inject.Singleton
import jakarta.transaction.Transactional

@Singleton
open class JpaTransactionBoundary : TransactionBoundary {
    @Transactional
    override fun <T> inTransaction(block: () -> T): T = block()
}
