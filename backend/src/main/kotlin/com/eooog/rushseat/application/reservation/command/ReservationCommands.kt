package com.eooog.rushseat.application.reservation.command

import java.time.Instant

data class HoldSeatCommand(
    val performanceId: Long,
    val performanceSeatId: Long,
    val memberId: Long,
    val idempotencyKey: String,
    val requestedAt: Instant,
)

data class ConfirmReservationCommand(
    val performanceId: Long,
    val memberId: Long,
    val holdToken: String,
    val requestedAt: Instant,
)
