package com.eooog.rushseat.application.reservation.required

import com.eooog.rushseat.domain.performance.PerformanceSalesStatus
import com.eooog.rushseat.domain.performance.PerformanceSeatStatus
import com.eooog.rushseat.domain.performance.PerformanceStatus
import com.eooog.rushseat.domain.reservation.ReservationStatus
import java.time.Instant

interface LoadPerformanceSalesStatusPort {
    fun load(performanceId: Long): PerformanceSalesStatusSnapshot?
}

data class PerformanceSalesStatusSnapshot(
    val performanceId: Long,
    val status: PerformanceStatus,
    val salesStatus: PerformanceSalesStatus,
) {
    fun isOnSale(): Boolean {
        return status == PerformanceStatus.SCHEDULED && salesStatus == PerformanceSalesStatus.ON_SALE
    }
}

interface LoadReservationPort {
    fun findByIdempotencyKey(
        performanceId: Long,
        memberId: Long,
        idempotencyKey: String,
    ): ReservationSnapshot?

    fun findByHoldToken(
        performanceId: Long,
        memberId: Long,
        holdToken: String,
    ): ReservationSnapshot?
}

data class ReservationSnapshot(
    val reservationId: Long,
    val performanceId: Long,
    val performanceSeatId: Long,
    val memberId: Long,
    val status: ReservationStatus,
    val holdToken: String,
    val expiresAt: Instant?,
)

interface HoldPerformanceSeatPort {
    fun hold(command: HoldPerformanceSeatCommand): HoldPerformanceSeatResult
}

data class HoldPerformanceSeatCommand(
    val performanceId: Long,
    val performanceSeatId: Long,
    val memberId: Long,
    val holdToken: String,
    val expiresAt: Instant,
)

data class HoldPerformanceSeatResult(
    val held: Boolean,
    val performanceSeatId: Long,
    val latestStatus: PerformanceSeatStatus? = null,
)

interface SaveReservationPort {
    fun saveHeld(command: SaveHeldReservationCommand): SavedReservationResult
}

data class SaveHeldReservationCommand(
    val performanceId: Long,
    val performanceSeatId: Long,
    val memberId: Long,
    val holdToken: String,
    val idempotencyKey: String,
    val expiresAt: Instant,
)

data class SavedReservationResult(
    val reservationId: Long,
)

interface ConfirmPerformanceSeatPort {
    fun confirm(command: ConfirmPerformanceSeatCommand): ConfirmPerformanceSeatResult
}

data class ConfirmPerformanceSeatCommand(
    val performanceId: Long,
    val memberId: Long,
    val holdToken: String,
    val requestedAt: Instant,
)

data class ConfirmPerformanceSeatResult(
    val confirmed: Boolean,
    val performanceSeatId: Long? = null,
)

interface ConfirmReservationPort {
    fun confirm(command: ConfirmReservationRecordCommand): ConfirmReservationRecordResult
}

data class ConfirmReservationRecordCommand(
    val performanceId: Long,
    val memberId: Long,
    val holdToken: String,
    val confirmedAt: Instant,
)

data class ConfirmReservationRecordResult(
    val reservationId: Long?,
)

interface PublishSeatChangePort {
    fun publishSeatHeld(event: SeatHeldEvent)
    fun publishSeatReserved(event: SeatReservedEvent)
}

data class SeatHeldEvent(
    val performanceId: Long,
    val performanceSeatId: Long,
    val holdExpiresAt: Instant,
)

data class SeatReservedEvent(
    val performanceId: Long,
    val performanceSeatId: Long,
)
