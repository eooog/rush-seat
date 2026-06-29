package com.eooog.rushseat.seat

import com.eooog.rushseat.queue.QueueService
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class SeatService(
    private val jdbc: JdbcClient,
    private val queueService: QueueService,
) {
    fun getSectorSummary(eventId: Long, authorization: String?): SectorSummaryResponse {
        queueService.requireAdmitted(authorization, eventId)

        val sectors = jdbc.sql(
            """
            SELECT
                display_sector_id,
                COUNT(*) FILTER (WHERE status = 'AVAILABLE') AS available_count,
                COUNT(*) FILTER (WHERE status = 'HELD') AS held_count,
                COUNT(*) FILTER (WHERE status = 'RESERVED') AS reserved_count
            FROM asset
            WHERE event_id = :eventId
            GROUP BY display_sector_id
            ORDER BY display_sector_id
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .query { rs, _ ->
                SectorSummaryDto(
                    displaySectorId = rs.getString("display_sector_id"),
                    availableCount = rs.getLong("available_count"),
                    heldCount = rs.getLong("held_count"),
                    reservedCount = rs.getLong("reserved_count"),
                )
            }
            .list()

        val version = jdbc.sql(
            """
            SELECT COALESCE(MAX(version), 0) AS version
            FROM subscription_tile
            WHERE event_id = :eventId
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .query(Long::class.java)
            .single()

        return SectorSummaryResponse(eventId = eventId, version = version, sectors = sectors)
    }

    fun getTileSnapshot(eventId: Long, tileId: String, authorization: String?): TileSnapshotResponse {
        queueService.requireAdmitted(authorization, eventId)

        val assets = jdbc.sql(
            """
            SELECT id, code, x, y, status, version, hold_expires_at
            FROM asset
            WHERE event_id = :eventId
              AND tile_id = :tileId
            ORDER BY id
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("tileId", tileId)
            .query { rs, _ ->
                AssetDto(
                    assetId = rs.getLong("id"),
                    code = rs.getString("code"),
                    x = rs.getInt("x"),
                    y = rs.getInt("y"),
                    status = rs.getString("status"),
                    assetVersion = rs.getLong("version"),
                    holdExpiresAt = rs.getObject("hold_expires_at", OffsetDateTime::class.java),
                )
            }
            .list()

        val tileVersion = jdbc.sql(
            """
            SELECT version
            FROM subscription_tile
            WHERE event_id = :eventId
              AND tile_id = :tileId
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("tileId", tileId)
            .query(Long::class.java)
            .optional()
            .orElse(0L)

        return TileSnapshotResponse(eventId = eventId, tileId = tileId, tileVersion = tileVersion, assets = assets)
    }

    fun findAssetState(eventId: Long, assetId: Long): AssetState? {
        return jdbc.sql(
            """
            SELECT
                a.id,
                a.event_id,
                a.tile_id,
                st.version AS tile_version,
                a.status,
                a.version,
                a.hold_expires_at
            FROM asset a
            JOIN subscription_tile st
              ON st.event_id = a.event_id
             AND st.tile_id = a.tile_id
            WHERE a.event_id = :eventId
              AND a.id = :assetId
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("assetId", assetId)
            .query { rs, _ ->
                AssetState(
                    assetId = rs.getLong("id"),
                    eventId = rs.getLong("event_id"),
                    tileId = rs.getString("tile_id"),
                    tileVersion = rs.getLong("tile_version"),
                    status = rs.getString("status"),
                    version = rs.getLong("version"),
                    holdExpiresAt = rs.getObject("hold_expires_at", OffsetDateTime::class.java),
                )
            }
            .optional()
            .orElse(null)
    }
}
