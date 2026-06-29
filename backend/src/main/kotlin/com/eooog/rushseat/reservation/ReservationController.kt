package com.eooog.rushseat.reservation

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class ReservationController(
    private val reservationService: ReservationService,
) {
    @PostMapping("/events/{eventId}/assets/{assetId}/hold")
    fun hold(
        @PathVariable eventId: Long,
        @PathVariable assetId: Long,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: HoldRequest,
    ): HoldResponse {
        return reservationService.hold(eventId, assetId, authorization, idempotencyKey, request)
    }

    @PostMapping("/events/{eventId}/reservations/confirm")
    fun confirm(
        @PathVariable eventId: Long,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: ConfirmRequest,
    ): ConfirmResponse {
        return reservationService.confirm(eventId, authorization, request)
    }
}
