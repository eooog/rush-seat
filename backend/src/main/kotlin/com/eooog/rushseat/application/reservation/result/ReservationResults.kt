package com.eooog.rushseat.application.reservation.result

import java.time.Instant

enum class HoldSeatResultStatus {
    HELD,
    ALREADY_PROCESSED,
    NOT_ON_SALE,
    UNAVAILABLE,
}

data class HoldSeatResult(
    val status: HoldSeatResultStatus,
    val reservationId: Long? = null,
    val performanceSeatId: Long,
    val holdToken: String? = null,
    val expiresAt: Instant? = null,
)

enum class ConfirmReservationResultStatus {
    CONFIRMED,
    NOT_CONFIRMABLE,
}

data class ConfirmReservationResult(
    val status: ConfirmReservationResultStatus,
    val reservationId: Long? = null,
    val performanceSeatId: Long? = null,
)
