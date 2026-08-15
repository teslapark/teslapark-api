package com.teslapark.domain.model

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class ParkingSessionTest {
    private val entryTime = Instant.parse("2026-08-15T12:00:00Z")
    private val plate = LicensePlate("ZUL0001")
    private val spot =
        Spot(
            externalId = 1,
            sectorCode = SectorCode("A"),
            coordinates = Coordinates.of("-23.561684", "-46.655981"),
        )

    private fun entered(): ParkingSession =
        ParkingSession.enter(
            licensePlate = plate,
            entryTime = entryTime,
            occupancyRateAtEntry = BigDecimal("0.4667"),
            priceMultiplier = BigDecimal("1.000"),
        )

    private fun parked(): ParkingSession = succeed(entered().park(spot, entryTime.plusSeconds(72)))

    private fun exited(): ParkingSession = succeed(parked().exit(entryTime.plus(Duration.ofMinutes(130))))

    private fun succeed(result: DomainResult<ParkingSession>): ParkingSession =
        result.shouldBeInstanceOf<DomainResult.Success<ParkingSession>>().value

    @Test
    fun `entered transitions to parked assigning spot and parked time`() {
        val session = parked()

        session.status shouldBe SessionStatus.PARKED
        session.spotExternalId shouldBe 1L
        session.sectorCode shouldBe SectorCode("A")
        session.parkedTime shouldBe entryTime.plusSeconds(72)
    }

    @Test
    fun `entered transitions straight to exited and still bills`() {
        val session = succeed(entered().exit(entryTime.plus(Duration.ofMinutes(130))))

        session.status shouldBe SessionStatus.EXITED
        session.spotExternalId shouldBe null
        session.stay shouldBe Duration.ofMinutes(130)
    }

    @Test
    fun `parked transitions to exited`() {
        exited().status shouldBe SessionStatus.EXITED
    }

    @Test
    fun `exited rejects any further transition`() {
        val closed = exited()

        closed.park(spot, entryTime.plusSeconds(1)).errorOrNull() shouldBe DomainError.SessionAlreadyClosed
        closed.exit(entryTime.plusSeconds(1)).errorOrNull() shouldBe DomainError.SessionAlreadyClosed
        closed.cancel().errorOrNull() shouldBe DomainError.SessionAlreadyClosed
    }

    @Test
    fun `parked never goes back to entered`() {
        val session = parked()

        session.park(spot, entryTime.plusSeconds(120)).errorOrNull() shouldBe
            DomainError.InvalidSessionTransition("PARKED", "PARKED")
    }

    @Test
    fun `exit before entry is rejected`() {
        val error = entered().exit(entryTime.minusSeconds(1)).errorOrNull()

        error.shouldBeInstanceOf<DomainError.ExitTimeBeforeEntryTime>()
    }

    @Test
    fun `parked before entry is rejected`() {
        val error = entered().park(spot, entryTime.minusSeconds(1)).errorOrNull()

        error.shouldBeInstanceOf<DomainError.ParkedTimeBeforeEntryTime>()
    }

    @Test
    fun `every transition returns a new instance and leaves the previous untouched`() {
        val before = entered()
        val after = succeed(before.park(spot, entryTime))

        before.status shouldBe SessionStatus.ENTERED
        before.spotExternalId shouldBe null
        after.status shouldBe SessionStatus.PARKED
    }

    @Test
    fun `cancel closes an open session`() {
        succeed(entered().cancel()).status shouldBe SessionStatus.CANCELLED
        succeed(parked().cancel()).isClosed shouldBe true
    }

    @Test
    fun `charge is attached without altering the state machine`() {
        val charged = exited().withCharge(Money.of("40.50"), billedHours = 3, amount = Money.of("121.50"))

        charged.status shouldBe SessionStatus.EXITED
        charged.amountCharged shouldBe Money.of("121.50")
        charged.billedHours shouldBe 3
    }
}
