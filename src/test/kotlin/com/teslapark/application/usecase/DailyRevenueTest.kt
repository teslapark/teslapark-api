package com.teslapark.application.usecase

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.model.LicensePlate
import com.teslapark.domain.model.Money
import com.teslapark.domain.model.ParkingSession
import com.teslapark.domain.model.RevenueEntry
import com.teslapark.domain.model.Sector
import com.teslapark.domain.model.SectorCode
import com.teslapark.domain.port.FixedClockProvider
import com.teslapark.domain.port.InMemoryParkingSessionRepository
import com.teslapark.domain.port.InMemoryRevenueRepository
import com.teslapark.domain.port.InMemorySectorRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DailyRevenueTest {
    private val now = Instant.parse("2026-08-15T14:32:10Z")
    private val today = LocalDate.parse("2026-08-15")

    private val clock = FixedClockProvider(now)
    private val revenue = InMemoryRevenueRepository()
    private val sectors = InMemorySectorRepository()
    private val sessions = InMemoryParkingSessionRepository()

    private val getDailyRevenue = GetDailyRevenue(revenue, sectors, clock)
    private val reconcile = ReconcileDailyRevenue(revenue, sessions, clock)

    private fun sector(
        code: String,
        price: String,
    ) = Sector(
        code = SectorCode(code),
        basePrice = Money.of(price),
        maxCapacity = 10,
        openHour = LocalTime.of(0, 0),
        closeHour = LocalTime.of(23, 59),
        durationLimit = Duration.ofMinutes(1440),
    )

    init {
        sectors.synchronize(listOf(sector("A", "40.50"), sector("B", "4.10")))
    }

    @Test
    fun `revenue reflects exactly the exits of the day`() {
        revenue.accumulate(RevenueEntry(SectorCode("A"), today, Money.of("121.50")))
        revenue.accumulate(RevenueEntry(SectorCode("A"), today, Money.of("40.50")))
        revenue.accumulate(RevenueEntry(SectorCode("B"), today, Money.of("13.53")))
        revenue.accumulate(RevenueEntry(SectorCode("A"), LocalDate.parse("2026-08-14"), Money.of("999.99")))

        val report = getDailyRevenue.execute(today, null).valueOrNull()!!

        report.total shouldBe Money.of("175.53")
        report.sectors shouldHaveSize 2
        report.sectors.first().sector shouldBe SectorCode("A")
        report.sectors.first().amount shouldBe Money.of("162.00")
        report.sessions shouldBe 3
    }

    @Test
    fun `a free session increments the free counter and not the amount`() {
        revenue.accumulate(RevenueEntry(SectorCode("B"), today, Money.of("13.53")))
        revenue.accumulate(RevenueEntry(SectorCode("B"), today, Money.zero()))
        revenue.accumulate(RevenueEntry(SectorCode("B"), today, Money.zero()))

        val report = getDailyRevenue.execute(today, SectorCode("B")).valueOrNull()!!

        report.total shouldBe Money.of("13.53")
        report.freeSessions shouldBe 2
        report.sessions shouldBe 3
    }

    @Test
    fun `an unknown sector is reported as not found`() {
        val error = getDailyRevenue.execute(today, SectorCode("Z")).errorOrNull()

        error.shouldBeInstanceOf<DomainError.SectorNotFound>()
    }

    @Test
    fun `a day without exits reports zero`() {
        val report = getDailyRevenue.execute(LocalDate.parse("2026-01-01"), null).valueOrNull()!!

        report.total shouldBe Money.zero()
        report.sectors shouldHaveSize 0
    }

    @Test
    fun `without a date the report uses the local day of the garage`() {
        revenue.accumulate(RevenueEntry(SectorCode("A"), today, Money.of("10.00")))

        getDailyRevenue.execute(null, null).valueOrNull()!!.revenueDate shouldBe today
    }

    @Test
    fun `reconciliation is balanced when the snapshot matches the sessions`() {
        revenue.accumulate(RevenueEntry(SectorCode("A"), today, Money.of("121.50")))
        sessions.save(exitedSession("ZUL0001", SectorCode("A"), Money.of("121.50")))

        reconcile.execute(today).isBalanced shouldBe true
    }

    @Test
    fun `reconciliation reports the sector whose snapshot drifted from the sessions`() {
        revenue.accumulate(RevenueEntry(SectorCode("A"), today, Money.of("121.50")))
        sessions.save(exitedSession("ZUL0001", SectorCode("A"), Money.of("121.50")))
        sessions.save(exitedSession("ZUL0002", SectorCode("A"), Money.of("40.50")))

        val report = reconcile.execute(today)

        report.isBalanced shouldBe false
        report.discrepancies shouldHaveSize 1
        report.discrepancies.first().snapshotTotal shouldBe Money.of("121.50")
        report.discrepancies.first().sessionsTotal shouldBe Money.of("162.00")
    }

    private fun exitedSession(
        plate: String,
        sector: SectorCode,
        amount: Money,
    ): ParkingSession =
        ParkingSession
            .enter(
                licensePlate = LicensePlate(plate),
                entryTime = now.minusSeconds(7800),
                occupancyRateAtEntry = BigDecimal("0.4667"),
                priceMultiplier = BigDecimal("1.000"),
            ).exit(now)
            .valueOrNull()!!
            .copy(sectorCode = sector)
            .withCharge(Money.of("40.50"), billedHours = 3, amount = amount, revenueDate = today)
}
