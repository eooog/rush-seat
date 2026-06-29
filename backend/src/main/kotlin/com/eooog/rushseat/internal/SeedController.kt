package com.eooog.rushseat.internal

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SeedController(
    private val seedService: SeedService,
) {
    @PostMapping("/internal/events/{eventId}/seed")
    fun seed(
        @PathVariable eventId: Long,
        @RequestBody request: SeedEventRequest,
    ): SeedEventResponse {
        return seedService.seed(eventId, request)
    }
}
