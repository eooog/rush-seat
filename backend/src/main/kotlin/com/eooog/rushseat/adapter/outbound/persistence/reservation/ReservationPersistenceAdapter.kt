package com.eooog.rushseat.adapter.outbound.persistence.reservation

import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.ConfirmPerformanceSeatResult
import com.eooog.rushseat.application.reservation.required.ConfirmReservationPort
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordCommand
import com.eooog.rushseat.application.reservation.required.ConfirmReservationRecordResult
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatCommand
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatPort
import com.eooog.rushseat.application.reservation.required.HoldPerformanceSeatResult
import com.eooog.rushseat.application.reservation.required.LoadPerformanceSalesStatusPort
import com.eooog.rushseat.application.reservation.required.LoadReservationPort
import com.eooog.rushseat.application.reservation.required.PerformanceSalesStatusSnapshot
import com.eooog.rushseat.application.reservation.required.ReservationSnapshot
import com.eooog.rushseat.application.reservation.required.SaveHeldReservationCommand
import com.eooog.rushseat.application.reservation.required.SaveReservationPort
import com.eooog.rushseat.application.reservation.required.SavedReservationResult
import com.eooog.rushseat.domain.performance.PerformanceSalesStatus
import com.eooog.rushseat.domain.performance.PerformanceSeatStatus
import com.eooog.rushseat.domain.performance.PerformanceStatus
import com.eooog.rushseat.domain.reservation.ReservationStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant

@Component
class ReservationPersistenceAdapter(
    private val jdbc: JdbcClient,
) : LoadPerformanceSalesStatusPort,
    LoadReservationPort,
    HoldPerformanceSeatPort,
    SaveReservationPort,
    ConfirmPerformanceSeatPort,
    ConfirmReservationPort {

    override fun load(performanceId: Long): PerformanceSalesStatusSnapshot? {
        return jdbc.sql(
            """
            SELECT id, status, sales_status
            FROM performance
            WHERE id = :performanceId
            """.trimIndent()
        )
            .param("performanceId", performanceId)
            .query { rs, _ ->
                PerformanceSalesStatusSnapshot(
                    performanceId = rs.getLong("id"),
                    status = PerformanceStatus.valueOf(rs.getString("status")),
                    salesStatus = PerformanceSalesStatus.valueOf(rs.getString("sales_status")),
                )
            }
            .optional()
            .orElse(null)
    }

    override fun findByIdempotencyKey(
        performanceId: Long,
        memberId: Long,
        idempotencyKey: String,
    ): ReservationSnapshot? {
        return jdbc.sql(
            """
            SELECT id, performance_id, performance_seat_id, member_id, status, hold_token, expires_at
            FROM reservation
            WHERE performance_id = :performanceId
              AND member_id = :memberId
              AND idempotency_key = :idempotencyKey
            """.trimIndent()
        )
            .param("performanceId", performanceId)
            .param("memberId", memberId)
            .param("idempotencyKey", idempotencyKey)
            .query { rs, _ -> rs.toReservationSnapshot() }
            .optional()
            .orElse(null)
    }

    override fun findByHoldToken(
        performanceId: Long,
        memberId: Long,
        holdToken: String,
    ): ReservationSnapshot? {
        return jdbc.sql(
            """
            SELECT id, performance_id, performance_seat_id, member_id, status, hold_token, expires_at
            FROM reservation
            WHERE performance_id = :performanceId
              AND member_id = :memberId
              AND hold_token = :holdToken
            """.trimIndent()
        )
            .param("performanceId", performanceId)
            .param("memberId", memberId)
            .param("holdToken", holdToken)
            .query { rs, _ -> rs.toReservationSnapshot() }
            .optional()
            .orElse(null)
    }

    override fun hold(command: HoldPerformanceSeatCommand): HoldPerformanceSeatResult {
        val updated = jdbc.sql(
            """
            UPDATE performance_seat
            SET status = 'HELD',
                hold_member_id = :memberId,
                hold_token = :holdToken,
                hold_expires_at = :expiresAt,
                version = version + 1,
                updated_at = now()
            WHERE id = :performanceSeatId
              AND performance_id = :performanceId
              AND status = 'AVAILABLE'
            """.trimIndent()
        )
            .param("memberId", command.memberId)
            .param("holdToken", command.holdToken)
            .param("expiresAt", command.expiresAt)
            .param("performanceSeatId", command.performanceSeatId)
            .param("performanceId", command.performanceId)
            .update()

        if (updated == 1) {
            return HoldPerformanceSeatResult(
                held = true,
                performanceSeatId = command.performanceSeatId,
                latestStatus = PerformanceSeatStatus.HELD,
            )
        }

        return HoldPerformanceSeatResult(
            held = false,
            performanceSeatId = command.performanceSeatId,
            latestStatus = loadPerformanceSeatStatus(
                performanceId = command.performanceId,
                performanceSeatId = command.performanceSeatId,
            ),
        )
    }

    override fun saveHeld(command: SaveHeldReservationCommand): SavedReservationResult {
        val reservationId = jdbc.sql(
            """
            INSERT INTO reservation(
                performance_id,
                performance_seat_id,
                member_id,
                status,
                hold_token,
                idempotency_key,
                expires_at
            )
            VALUES (
                :performanceId,
                :performanceSeatId,
                :memberId,
                'HELD',
                :holdToken,
                :idempotencyKey,
                :expiresAt
            )
            ON CONFLICT (performance_id, member_id, idempotency_key)
            DO UPDATE SET updated_at = reservation.updated_at
            RETURNING id
            """.trimIndent()
        )
            .param("performanceId", command.performanceId)
            .param("performanceSeatId", command.performanceSeatId)
            .param("memberId", command.memberId)
            .param("holdToken", command.holdToken)
            .param("idempotencyKey", command.idempotencyKey)
            .param("expiresAt", command.expiresAt)
            .query(Long::class.java)
            .single()

        return SavedReservationResult(reservationId = reservationId)
    }

    override fun confirm(command: ConfirmPerformanceSeatCommand): ConfirmPerformanceSeatResult {
        val performanceSeatId = jdbc.sql(
            """
            UPDATE performance_seat
            SET status = 'RESERVED',
                hold_member_id = NULL,
                hold_token = NULL,
                hold_expires_at = NULL,
                version = version + 1,
                updated_at = now()
            WHERE performance_id = :performanceId
              AND hold_member_id = :memberId
              AND hold_token = :holdToken
              AND status = 'HELD'
              AND hold_expires_at > :requestedAt
            RETURNING id
            """.trimIndent()
        )
            .param("performanceId", command.performanceId)
            .param("memberId", command.memberId)
            .param("holdToken", command.holdToken)
            .param("requestedAt", command.requestedAt)
            .query(Long::class.java)
            .optional()
            .orElse(null)

        return ConfirmPerformanceSeatResult(
            confirmed = performanceSeatId != null,
            performanceSeatId = performanceSeatId,
        )
    }

    override fun confirm(command: ConfirmReservationRecordCommand): ConfirmReservationRecordResult {
        val reservationId = jdbc.sql(
            """
            UPDATE reservation
            SET status = 'CONFIRMED',
                confirmed_at = :confirmedAt,
                updated_at = now()
            WHERE performance_id = :performanceId
              AND member_id = :memberId
              AND hold_token = :holdToken
              AND status = 'HELD'
              AND expires_at > :confirmedAt
            RETURNING id
            """.trimIndent()
        )
            .param("performanceId", command.performanceId)
            .param("memberId", command.memberId)
            .param("holdToken", command.holdToken)
            .param("confirmedAt", command.confirmedAt)
            .query(Long::class.java)
            .optional()
            .orElse(null)

        return ConfirmReservationRecordResult(reservationId = reservationId)
    }

    private fun loadPerformanceSeatStatus(
        performanceId: Long,
        performanceSeatId: Long,
    ): PerformanceSeatStatus? {
        return jdbc.sql(
            """
            SELECT status
            FROM performance_seat
            WHERE performance_id = :performanceId
              AND id = :performanceSeatId
            """.trimIndent()
        )
            .param("performanceId", performanceId)
            .param("performanceSeatId", performanceSeatId)
            .query { rs, _ -> PerformanceSeatStatus.valueOf(rs.getString("status")) }
            .optional()
            .orElse(null)
    }

    private fun ResultSet.toReservationSnapshot(): ReservationSnapshot {
        return ReservationSnapshot(
            reservationId = getLong("id"),
            performanceId = getLong("performance_id"),
            performanceSeatId = getLong("performance_seat_id"),
            memberId = getLong("member_id"),
            status = ReservationStatus.valueOf(getString("status")),
            holdToken = getString("hold_token"),
            expiresAt = getTimestamp("expires_at")?.toInstant(),
        )
    }
}
