package com.teslapark.infrastructure.health

import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional

@Singleton
open class DatabaseProbe(
    private val entityManager: EntityManager,
) {
    @Transactional
    open fun isReachable(): Boolean = runCatching { entityManager.createNativeQuery(PROBE).singleResult != null }.getOrDefault(false)

    private companion object {
        const val PROBE = "SELECT 1"
    }
}
