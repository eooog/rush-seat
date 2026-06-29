package com.eooog.rushseat.reservation

import com.eooog.rushseat.realtime.AssetEventPublisher
import com.eooog.rushseat.seat.AssetState
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class ExpiredHoldReaper(
    private val jdbc: JdbcClient,
    private val assetEventPublisher: AssetEventPublisher,
) {
    @Transactional
    @Scheduled(fixedDelayString = "\${rush-seat.reaper.fixed-delay-ms}")
    fun releaseExpiredHolds() {
        val released = jdbc.sql(
            """
            WITH expired AS (
                SELECT id, event_id, tile_id
                FROM asset
                WHERE status = 'HELD'
                  AND hold_expires_at <= now()
                LIMIT 500
                FOR UPDATE SKIP LOCKED
            ), updated_asset AS (
                UPDATE asset a
                SET status = 'AVAILABLE',
                    hold_owner_id = NULL,
                    hold_token = NULL,
                    hold_expires_at = NULL,
                    version = version + 1,
                    updated_at = now()
                FROM expired e
                WHERE a.id = e.id
                RETURNING a.id, a.event_id, a.tile_id, a.version, a.hold_expires_at
            ), updated_tile AS (
                UPDATE subscription_tile st
                SET version = version + 1
                FROM (SELECT DISTINCT event_id, tile_id FROM updated_asset) ua
                WHERE st.event_id = ua.event_id
                  AND st.tile_id = ua.tile_id
                RETURNING st.event_id, st.tile_id, st.version
            )
            SELECT
                ua.id,
                ua.event_id,
                ua.tile_id,
                ut.version AS tile_version,
                ua.version AS asset_version,
                ua.hold_expires_at
            FROM updated_asset ua
            JOIN updated_tile ut
              ON ut.event_id = ua.event_id
             AND ut.tile_id = ua.tile_id
            """.trimIndent(),
        )
            .query { rs, _ ->
                AssetState(
                    assetId = rs.getLong("id"),
                    eventId = rs.getLong("event_id"),
                    tileId = rs.getString("tile_id"),
                    tileVersion = rs.getLong("tile_version"),
                    status = "AVAILABLE",
                    version = rs.getLong("asset_version"),
                    holdExpiresAt = rs.getObject("hold_expires_at", OffsetDateTime::class.java),
                )
            }
            .list()

        released.forEach(assetEventPublisher::publishState)

        if (released.isNotEmpty()) {
            jdbc.sql(
                """
                UPDATE reservation
                SET status = 'EXPIRED', updated_at = now()
                WHERE status = 'HELD'
                  AND expires_at <= now()
                """.trimIndent(),
            ).update()
        }
    }
}
