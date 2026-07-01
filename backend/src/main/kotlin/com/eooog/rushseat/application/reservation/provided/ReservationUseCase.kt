package com.eooog.rushseat.application.reservation.provided

import com.eooog.rushseat.application.reservation.ConfirmReservationCommand
import com.eooog.rushseat.application.reservation.ConfirmReservationResult
import com.eooog.rushseat.application.reservation.HoldSeatCommand
import com.eooog.rushseat.application.reservation.HoldSeatResult

interface HoldSeatUseCase {
    fun hold(command: HoldSeatCommand): HoldSeatResult
}

interface ConfirmReservationUseCase {
    fun confirm(command: ConfirmReservationCommand): ConfirmReservationResult
}
