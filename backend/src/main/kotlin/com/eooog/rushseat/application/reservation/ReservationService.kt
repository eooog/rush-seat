package com.eooog.rushseat.application.reservation

import com.eooog.rushseat.application.reservation.provided.ConfirmReservationUseCase
import com.eooog.rushseat.application.reservation.provided.HoldSeatUseCase
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordCommand
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.reservation.required.LoadReservationPort
import com.eooog.rushseat.application.reservation.required.PublishSeatChangePort
import com.eooog.rushseat.application.reservation.required.SaveHeldReservationCommand
import com.eooog.rushseat.application.reservation.required.SaveReservationPort
import com.eooog.rushseat.application.reservation.required.SeatHeldEvent
import com.eooog.rushseat.application.reservation.required.SeatReservedEvent
import com.eooog.rushseat.domain.reservation.ReservationStatus
import java.time.Duration
import java.util.UUID

class ReservationService(
    private val loadPerformanceSalesStatusPort: LoadPerformanceSalesStatusPort,
    private val loadReservationPort: LoadReservationPort,
    private val holdPerformanceSeatPort: HoldPerformanceSeatPort,
    private val saveReservationPort: SaveReservationPort,
    private val confirmPerformanceSeatPort: ConfirmPerformanceSeatPort,
    private val confirmReservationPort: ConfirmReservationPort,
    private val publishSeatChangePort: PublishSeatChangePort,
    private val holdTtl: Duration = Duration.ofMinutes(3),
) : HoldSeatUseCase, ConfirmReservationUseCase {

    override fun hold(command: HoldSeatCommand): HoldSeatResult {
        val performance = loadPerformanceSalesStatusPort.load(command.performanceId)
            ?: return HoldSeatResult(
                status = HoldSeatResultStatus.NOT_ON_SALE,
                performanceSeatId = command.performanceSeatId,
            )

        if (!performance.isOnSale()) {
            return HoldSeatResult(
                status = HoldSeatResultStatus.NOT_ON_SALE,
                performanceSeatId = command.performanceSeatId,
            )
        }

        val existing = loadReservationPort.findByIdempotencyKey(
            performanceId = command.performanceId,
            memberId = command.memberId,
            idempotencyKey = command.idempotencyKey,
        )

        if (existing != null) {
            return HoldSeatResult(
                status = HoldSeatResultStatus.ALREADY_PROCESSED,
                reservationId = existing.reservationId,
                performanceSeatId = existing.performanceSeatId,
                holdToken = existing.holdToken.takeIf { existing.status == ReservationStatus.HELD },
                expiresAt = existing.expiresAt,
            )
        }

        val holdToken = "ht_${UUID.randomUUID()}"
        val expiresAt = command.requestedAt.plus(holdTtl)

        val holdResult = holdPerformanceSeatPort.hold(
            HoldPerformanceSeatCommand(
                performanceId = command.performanceId,
                performanceSeatId = command.performanceSeatId,
                memberId = command.memberId,
                holdToken = holdToken,
                expiresAt = expiresAt,
            )
        )

        if (!holdResult.held) {
            return HoldSeatResult(
                status = HoldSeatResultStatus.UNAVAILABLE,
                performanceSeatId = command.performanceSeatId,
            )
        }

        val savedReservation = saveReservationPort.saveHeld(
            SaveHeldReservationCommand(
                performanceId = command.performanceId,
                performanceSeatId = command.performanceSeatId,
                memberId = command.memberId,
                holdToken = holdToken,
                idempotencyKey = command.idempotencyKey,
                expiresAt = expiresAt,
            )
        )

        publishSeatChangePort.publishSeatHeld(
            SeatHeldEvent(
                performanceId = command.performanceId,
                performanceSeatId = command.performanceSeatId,
                holdExpiresAt = expiresAt,
            )
        )

        return HoldSeatResult(
            status = HoldSeatResultStatus.HELD,
            reservationId = savedReservation.reservationId,
            performanceSeatId = command.performanceSeatId,
            holdToken = holdToken,
            expiresAt = expiresAt,
        )
    }

    override fun confirm(command: ConfirmReservationCommand): ConfirmReservationResult {
        val seatResult = confirmPerformanceSeatPort.confirm(
            ConfirmPerformanceSeatCommand(
                performanceId = command.performanceId,
                memberId = command.memberId,
                holdToken = command.holdToken,
                requestedAt = command.requestedAt,
            )
        )

        if (!seatResult.confirmed || seatResult.performanceSeatId == null) {
            return ConfirmReservationResult(
                status = ConfirmReservationResultStatus.NOT_CONFIRMABLE,
            )
        }

        val reservationResult = confirmReservationPort.confirm(
            ConfirmReservationRecordCommand(
                performanceId = command.performanceId,
                memberId = command.memberId,
                holdToken = command.holdToken,
                confirmedAt = command.requestedAt,
            )
        )

        publishSeatChangePort.publishSeatReserved(
            SeatReservedEvent(
                performanceId = command.performanceId,
                performanceSeatId = seatResult.performanceSeatId,
            )
        )

        return ConfirmReservationResult(
            status = ConfirmReservationResultStatus.CONFIRMED,
            reservationId = reservationResult.reservationId,
            performanceSeatId = seatResult.performanceSeatId,
        )
    }
}
