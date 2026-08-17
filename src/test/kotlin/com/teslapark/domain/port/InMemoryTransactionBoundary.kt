package com.teslapark.domain.port

class InMemoryTransactionBoundary : TransactionBoundary {
    var transactions: Int = 0
        private set

    override fun <T> inTransaction(block: () -> T): T {
        transactions++
        return block()
    }
}
