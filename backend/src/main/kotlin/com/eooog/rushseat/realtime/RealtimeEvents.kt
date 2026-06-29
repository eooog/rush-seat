package com.eooog.rushseat.realtime

import java.time.Instant
import java.time.OffsetDateTime

data class SubscribeTileCommand(
    val type: String,
    val eventId: Long,
    val tileId: String,
    val lastSeenTileVersion: Long? = null,
)

data class TileSubscribedEvent(
    val type: String = "TILE_SUBSCRIBED",
    val eventId: Long,
    val tileId: String,
)

data class TileResyncRequiredEvent(
    val type: String = "TILE_RESYNC_REQUIRED",
    val eventId: Long,
    val tileId: String,
    val currentTileVersion: Long,
)

data class AssetChangedBatchEvent(
    val type: String = "ASSET_CHANGED_BATCH",
    val eventId: Long,
    val tileId: String,
    val tileVersion: Long,
    val publishedAt: Instant = Instant.now(),
    val changes: List<AssetChangeDto>,
)

data class AssetChangeDto(
    val assetId: Long,
    val status: String,
    val assetVersion: Long,
    val holdExpiresAt: OffsetDateTime? = null,
)
