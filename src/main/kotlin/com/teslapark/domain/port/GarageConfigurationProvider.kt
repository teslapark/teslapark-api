package com.teslapark.domain.port

import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.model.GarageConfiguration

interface GarageConfigurationProvider {
    fun fetchConfiguration(): DomainResult<GarageConfiguration>
}
