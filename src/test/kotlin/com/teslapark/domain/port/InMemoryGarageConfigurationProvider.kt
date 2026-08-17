package com.teslapark.domain.port

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import com.teslapark.domain.error.asSuccess
import com.teslapark.domain.model.GarageConfiguration

class InMemoryGarageConfigurationProvider(
    private var configuration: GarageConfiguration? = null,
    private var failure: DomainError? = null,
) : GarageConfigurationProvider {
    var fetchCount: Int = 0
        private set

    override fun fetchConfiguration(): DomainResult<GarageConfiguration> {
        fetchCount++
        failure?.let { return it.asFailure() }
        return configuration?.asSuccess() ?: DomainError.GarageConfigurationUnavailable.asFailure()
    }

    fun respondWith(configuration: GarageConfiguration) {
        this.configuration = configuration
        this.failure = null
    }

    fun failWith(error: DomainError) {
        this.failure = error
    }
}
