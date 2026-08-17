package com.teslapark.application.usecase

import com.teslapark.domain.GarageFixtures
import com.teslapark.domain.error.DomainError
import com.teslapark.domain.model.GarageConfigurationStatus
import com.teslapark.domain.port.FixedClockProvider
import com.teslapark.domain.port.InMemoryGarageConfigurationProvider
import com.teslapark.domain.port.InMemoryGarageStateRepository
import com.teslapark.domain.port.InMemorySectorRepository
import com.teslapark.domain.port.InMemorySpotRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncGarageConfigurationTest {
    private val now = Instant.parse("2026-08-15T09:00:03Z")

    private val clock = FixedClockProvider(now)
    private val sectors = InMemorySectorRepository()
    private val spots = InMemorySpotRepository()
    private val garageState = InMemoryGarageStateRepository()
    private val provider = InMemoryGarageConfigurationProvider()

    private val useCase = SyncGarageConfiguration(provider, sectors, spots, garageState, clock)

    private fun configuration() = GarageFixtures.configuration()

    @Test
    fun `synchronization populates sectors spots and total capacity`() {
        provider.respondWith(configuration())

        val summary = useCase.execute().valueOrNull().shouldNotBeNull()

        summary.status shouldBe GarageConfigurationStatus.SYNCED
        summary.sectors shouldBe 2
        summary.spots shouldBe 30
        summary.totalCapacity shouldBe 30
        summary.syncedAt shouldBe now
        summary.firstSynchronization shouldBe true

        sectors.findAll() shouldHaveSize 2
        sectors.totalCapacity() shouldBe 30
        garageState.currentStatus() shouldBe GarageConfigurationStatus.SYNCED
        garageState.lastSyncAt() shouldBe now
    }

    @Test
    fun `running the synchronization twice does not duplicate records`() {
        provider.respondWith(configuration())

        useCase.execute()
        val second = useCase.execute().valueOrNull().shouldNotBeNull()

        second.sectors shouldBe 2
        second.spots shouldBe 30
        second.firstSynchronization shouldBe false
        sectors.findAll() shouldHaveSize 2
        provider.fetchCount shouldBe 2
    }

    @Test
    fun `an unavailable gate control system keeps the configuration pending`() {
        useCase.execute().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable

        garageState.currentStatus() shouldBe GarageConfigurationStatus.PENDING
        useCase.isSynchronized() shouldBe false
        sectors.findAll() shouldHaveSize 0
    }

    @Test
    fun `the configuration recovers once the gate control system comes back`() {
        useCase.execute().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable

        provider.respondWith(configuration())
        clock.advanceTo(now.plusSeconds(60))

        useCase
            .execute()
            .valueOrNull()
            .shouldNotBeNull()
            .status shouldBe GarageConfigurationStatus.SYNCED
        useCase.isSynchronized() shouldBe true
        garageState.lastSyncAt() shouldBe now.plusSeconds(60)
    }

    @Test
    fun `a synchronized configuration becomes stale when the source goes down`() {
        provider.respondWith(configuration())
        useCase.execute()

        provider.failWith(DomainError.GarageConfigurationUnavailable)
        useCase.execute().errorOrNull() shouldBe DomainError.GarageConfigurationUnavailable

        garageState.currentStatus() shouldBe GarageConfigurationStatus.STALE
        garageState.totalCapacity() shouldBe 30
    }
}
