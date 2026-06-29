package com.eooog.rushseat.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.ceil

@Service
class SeedService(
    private val jdbc: JdbcClient,
) {
    @Transactional
    fun seed(eventId: Long, request: SeedEventRequest): SeedEventResponse {
        val totalSeats = request.totalSeats.coerceIn(1, 200_000)
        val displaySectorCount = request.displaySectorCount.coerceIn(1, 200)
        val tileSize = request.tileSize.coerceIn(100, 2_000)
        val tileCount = ceil(totalSeats.toDouble() / tileSize.toDouble()).toInt()
        val sectorSize = ceil(totalSeats.toDouble() / displaySectorCount.toDouble()).toInt()

        jdbc.sql("DELETE FROM reservation WHERE event_id = :eventId")
            .param("eventId", eventId)
            .update()
        jdbc.sql("DELETE FROM asset WHERE event_id = :eventId")
            .param("eventId", eventId)
            .update()
        jdbc.sql("DELETE FROM subscription_tile WHERE event_id = :eventId")
            .param("eventId", eventId)
            .update()
        jdbc.sql("DELETE FROM display_sector WHERE event_id = :eventId")
            .param("eventId", eventId)
            .update()

        jdbc.sql(
            """
            INSERT INTO seat_event(id, name, status)
            VALUES (:eventId, :eventName, 'OPEN')
            ON CONFLICT (id) DO UPDATE
            SET name = excluded.name,
                status = excluded.status,
                updated_at = now()
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("eventName", request.eventName)
            .update()

        jdbc.sql(
            """
            INSERT INTO display_sector(event_id, display_sector_id, name)
            SELECT :eventId,
                   'S-' || gs::text,
                   'Sector ' || gs::text
            FROM generate_series(1, :displaySectorCount) AS gs
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("displaySectorCount", displaySectorCount)
            .update()

        jdbc.sql(
            """
            INSERT INTO subscription_tile(event_id, tile_id, display_sector_id, name)
            SELECT :eventId,
                   'T-' || lpad(gs::text, 4, '0'),
                   'S-' || LEAST(:displaySectorCount, (((((gs - 1) * :tileSize) / :sectorSize) + 1)))::text,
                   'Tile ' || gs::text
            FROM generate_series(1, :tileCount) AS gs
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("tileSize", tileSize)
            .param("sectorSize", sectorSize)
            .param("tileCount", tileCount)
            .param("displaySectorCount", displaySectorCount)
            .update()

        jdbc.sql(
            """
            INSERT INTO asset(id, event_id, display_sector_id, tile_id, code, x, y, status)
            SELECT gs::bigint,
                   :eventId,
                   'S-' || LEAST(:displaySectorCount, (((gs - 1) / :sectorSize) + 1))::text,
                   'T-' || lpad((((gs - 1) / :tileSize) + 1)::text, 4, '0'),
                   'A-' || gs::text,
                   ((gs - 1) % 1000)::int,
                   (((gs - 1) / 1000))::int,
                   'AVAILABLE'
            FROM generate_series(1, :totalSeats) AS gs
            """.trimIndent(),
        )
            .param("eventId", eventId)
            .param("totalSeats", totalSeats)
            .param("tileSize", tileSize)
            .param("sectorSize", sectorSize)
            .param("displaySectorCount", displaySectorCount)
            .update()

        return SeedEventResponse(
            eventId = eventId,
            totalSeats = totalSeats,
            displaySectorCount = displaySectorCount,
            tileCount = tileCount,
        )
    }
}
