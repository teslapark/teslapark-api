package com.teslapark.domain.port

interface TransactionBoundary {
    fun <T> inTransaction(block: () -> T): T
}
