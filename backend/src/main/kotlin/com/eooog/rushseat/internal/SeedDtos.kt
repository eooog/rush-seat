package com.eooog.rushseat.internal

data class SeedEventRequest(
    val eventName: String = "Rush Seat Test Event",
    val totalSeats: Int = 100_000,
    val displaySectorCount: Int = 20,
    val tileSize: Int = 500,
)

data class SeedEventResponse(
    val eventId: Long,
    val totalSeats: Int,
    val displaySectorCount: Int,
    val tileCount: Int,
)
