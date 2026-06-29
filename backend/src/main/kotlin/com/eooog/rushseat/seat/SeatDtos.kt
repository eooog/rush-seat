package com.eooog.rushseat.seat

import java.time.OffsetDateTime

data class SectorSummaryResponse(
    val eventId: Long,
    val version: Long,
    val sectors: List<SectorSummaryDto>,
)

data class SectorSummaryDto(
    val displaySectorId: String,
    val availableCount: Long,
    val heldCount: Long,
    val reservedCount: Long,
)

data class TileSnapshotResponse(
    val eventId: Long,
    val tileId: String,
    val tileVersion: Long,
    val assets: List<AssetDto>,
)

data class AssetDto(
    val assetId: Long,
    val code: String,
    val x: Int,
    val y: Int,
    val status: String,
    val assetVersion: Long,
    val holdExpiresAt: OffsetDateTime?,
)

data class AssetState(
    val assetId: Long,
    val eventId: Long,
    val tileId: String,
    val status: String,
    val version: Long,
    val holdExpiresAt: OffsetDateTime?,
)
