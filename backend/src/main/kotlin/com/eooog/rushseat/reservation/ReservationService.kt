package com.eooog.rushseat.reservation

import com.eooog.rushseat.common.ApiError
import com.eooog.rushseat.queue.QueueService
import com.eooog.rushseat.realtime.AssetEventPublisher
import com.eooog.rushseat.seat.SeatService
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class ReservationService(
    private val jdbc: JdbcClient,
    private val redis: StringRedisTemplate,
    private val queueService: QueueService,
    private val seatService: SeatService,
    private val assetEventPublisher: AssetEventPublisher,
    @Value("\${rushmore-seat.hold.ttl-seconds}") private val holdTtlSeconds: Long,
    @Value("\${rushmore-seat.hold.claim-ttl-millis}") private val claimTtlMillis: Long,
) {
    @Transactional
    fun hold(eventId: Long, assetId: Long, authorization: String?, idempotencyKey: String?, request: HoldRequest): HoldResponse {
        val admitted = queueService.requireAdmitted(authorization, eventId)
        val effectiveIdempotencyKey = idempotencyKey ?: "auto_${UUID.randomUUID()}"
        findExistingHold(eventId, admitted.userId, effectiveIdempotencyKey)?.let { return it }

        val claimKey = "asset:claim:$eventId:$assetId"
        val claimed = redis.opsForValue().setIfAbsent(claimKey, admitted.userId, Duration.ofMillis(claimTtlMillis)) == true
        if (!claimed) throw ApiError("ASSET_CLAIMING", "Another user is trying to hold this asset", HttpStatus.CONFLICT)

        try {
            assetEventPublisher.publishClaiming(eventId, request.tileId, assetId, request.observedAssetVersion)
            val holdToken = "ht_${UUID.randomUUID()}"
            val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(holdTtlSeconds)

            val updated = jdbc.sql(
                """
                UPDATE asset
                SET status = 'HELD',
                    hold_owner_id = :userId,
                    hold_token = :holdToken,
                    hold_expires_at = :expiresAt,
                    version = version + 1,
                    updated_at = now()
                WHERE id = :assetId
                  AND event_id = :eventId
                  AND tile_id = :tileId
                  AND status = 'AVAILABLE'
                """.trimIndent(),
            )
                .param("userId", admitted.userId)
                .param("holdToken", holdToken)
                .param("expiresAt", expiresAt)
                .param("assetId", assetId)
                .param("eventId", eventId)
                .param("tileId", request.tileId)
                .update()

            if (updated == 0) throw ApiError("ASSET_ALREADY_HELD", "Asset is already held or reserved", HttpStatus.CONFLICT)

            incrementTileVersion(eventId, request.tileId)
            insertReservation(eventId, assetId, admitted.userId, holdToken, effectiveIdempotencyKey, expiresAt)
            seatService.findAssetState(eventId, assetId)?.let(assetEventPublisher::publishState)
            return HoldResponse(holdToken = holdToken, assetId = assetId, status = "HELD", expiresAt = expiresAt)
        } finally {
            redis.delete(claimKey)
        }
    }

    @Transactional
    fun confirm(eventId: Long, authorization: String?, request: ConfirmRequest): ConfirmResponse {
        val admitted = queueService.requireAdmitted(authorization, eventId)
        val asset = findAssetByHoldToken(eventId, request.holdToken)
            ?: throw ApiError("HOLD_NOT_FOUND", "Hold token was not found", HttpStatus.NOT_FOUND)

        val updated = jdbc.sql(
            """
            UPDATE asset
            SET status = 'RESERVED',
                version = version + 1,
                updated_at = now()
            WHERE event_id = :eventId
              AND hold_token = :holdToken
              AND hold_owner_id = :userId
              AND status = 'HELD'
              AND hold_expires_at > now()
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("holdToken", request.holdToken)
            .param("userId", admitted.userId)
            .update()

        if (updated == 0) throw ApiError("HOLD_NOT_CONFIRMABLE", "Hold is expired or not confirmable", HttpStatus.CONFLICT)
        incrementTileVersion(eventId, asset.tileId)
        val reservationId = confirmReservation(eventId, request.holdToken, admitted.userId)
        seatService.findAssetState(eventId, asset.assetId)?.let(assetEventPublisher::publishState)
        return ConfirmResponse(reservationId = reservationId, assetId = asset.assetId, status = "CONFIRMED")
    }

    private fun insertReservation(eventId: Long, assetId: Long, userId: String, holdToken: String, idempotencyKey: String, expiresAt: OffsetDateTime) {
        jdbc.sql(
            """
            INSERT INTO reservation(event_id, asset_id, user_id, status, hold_token, idempotency_key, expires_at)
            VALUES (:eventId, :assetId, :userId, 'HELD', :holdToken, :idempotencyKey, :expiresAt)
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("assetId", assetId)
            .param("userId", userId)
            .param("holdToken", holdToken)
            .param("idempotencyKey", idempotencyKey)
            .param("expiresAt", expiresAt)
            .update()
    }

    private fun confirmReservation(eventId: Long, holdToken: String, userId: String): Long? {
        return jdbc.sql(
            """
            UPDATE reservation
            SET status = 'CONFIRMED', confirmed_at = now(), updated_at = now()
            WHERE event_id = :eventId AND hold_token = :holdToken AND user_id = :userId
            RETURNING id
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("holdToken", holdToken)
            .param("userId", userId)
            .query(Long::class.java)
            .optional()
            .orElse(null)
    }

    private fun findExistingHold(eventId: Long, userId: String, idempotencyKey: String): HoldResponse? {
        return jdbc.sql(
            """
            SELECT asset_id, hold_token, status, expires_at
            FROM reservation
            WHERE event_id = :eventId AND user_id = :userId AND idempotency_key = :idempotencyKey
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("userId", userId)
            .param("idempotencyKey", idempotencyKey)
            .query { rs, _ ->
                HoldResponse(
                    holdToken = rs.getString("hold_token"),
                    assetId = rs.getLong("asset_id"),
                    status = rs.getString("status"),
                    expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                )
            }
            .optional()
            .orElse(null)
    }

    private fun findAssetByHoldToken(eventId: Long, holdToken: String): HeldAsset? {
        return jdbc.sql(
            """
            SELECT id, tile_id
            FROM asset
            WHERE event_id = :eventId AND hold_token = :holdToken
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("holdToken", holdToken)
            .query { rs, _ -> HeldAsset(assetId = rs.getLong("id"), tileId = rs.getString("tile_id")) }
            .optional()
            .orElse(null)
    }

    private fun incrementTileVersion(eventId: Long, tileId: String): Long {
        return jdbc.sql(
            """
            UPDATE subscription_tile
            SET version = version + 1
            WHERE event_id = :eventId AND tile_id = :tileId
            RETURNING version
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("tileId", tileId)
            .query(Long::class.java)
            .single()
    }

    private data class HeldAsset(val assetId: Long, val tileId: String)
}
