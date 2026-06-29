package com.eooog.rushseat.reservation

import java.time.OffsetDateTime

data class HoldRequest(
    val tileId: String,
    val observedAssetVersion: Long? = null,
    val observedTileVersion: Long? = null,
)

data class HoldResponse(
    val holdToken: String,
    val assetId: Long,
    val status: String,
    val expiresAt: OffsetDateTime?,
)

data class ConfirmRequest(
    val holdToken: String,
    val paymentId: String? = null,
)

data class ConfirmResponse(
    val reservationId: Long?,
    val assetId: Long,
    val status: String,
)
