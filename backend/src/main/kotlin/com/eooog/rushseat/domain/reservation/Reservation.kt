package com.eooog.rushseat.domain.reservation

import com.eooog.rushseat.domain.AuditableEntity
import com.eooog.rushseat.domain.member.Member
import com.eooog.rushseat.domain.performance.Performance
import com.eooog.rushseat.domain.performance.PerformanceSeat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "reservation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_reservation_idempotency",
            columnNames = ["performance_id", "member_id", "idempotency_key"]
        )
    ]
)
class Reservation protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "performance_id", nullable = false)
    lateinit var performance: Performance
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "performance_seat_id", nullable = false)
    lateinit var performanceSeat: PerformanceSeat
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "member_id", nullable = false)
    lateinit var member: Member
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 30)
    var status: ReservationStatus = ReservationStatus.HELD
        protected set

    @field:Column(name = "hold_token", nullable = false, length = 120)
    lateinit var holdToken: String
        protected set

    @field:Column(name = "idempotency_key", nullable = false, length = 120)
    lateinit var idempotencyKey: String
        protected set

    @field:Column(name = "expires_at")
    var expiresAt: Instant? = null
        protected set

    @field:Column(name = "confirmed_at")
    var confirmedAt: Instant? = null
        protected set

    fun confirm(now: Instant) {
        assertHeld()
        check(!isExpired(now)) {
            "만료된 예약은 확정할 수 없습니다"
        }

        this.status = ReservationStatus.CONFIRMED
        this.confirmedAt = now
    }

    fun expire(now: Instant) {
        assertHeld()
        check(isExpired(now)) {
            "아직 예약 만료 시간이 지나지 않았습니다"
        }

        this.status = ReservationStatus.EXPIRED
    }

    fun cancel() {
        assertHeld()
        this.status = ReservationStatus.CANCELLED
    }

    fun isExpired(now: Instant): Boolean {
        return expiresAt != null && !expiresAt!!.isAfter(now)
    }

    fun assertHeld() {
        check(status == ReservationStatus.HELD) {
            "임시 선점 상태의 예약이 아닙니다"
        }
    }

    companion object {
        fun createHeld(
            performance: Performance,
            performanceSeat: PerformanceSeat,
            member: Member,
            holdToken: String,
            idempotencyKey: String,
            expiresAt: Instant,
        ): Reservation {
            require(performanceSeat.performance == performance) {
                "예약 좌석은 동일한 공연 회차에 속해야 합니다"
            }

            return Reservation().apply {
                this.performance = performance
                this.performanceSeat = performanceSeat
                this.member = member
                this.status = ReservationStatus.HELD
                this.holdToken = validateHoldToken(holdToken)
                this.idempotencyKey = validateIdempotencyKey(idempotencyKey)
                this.expiresAt = expiresAt
                this.confirmedAt = null
            }
        }

        private fun validateHoldToken(holdToken: String): String {
            val normalized = holdToken.trim()
            require(normalized.isNotBlank()) { "좌석 선점 토큰은 비어 있을 수 없습니다" }
            require(normalized.length <= 120) { "좌석 선점 토큰은 120자를 초과할 수 없습니다" }
            return normalized
        }

        private fun validateIdempotencyKey(idempotencyKey: String): String {
            val normalized = idempotencyKey.trim()
            require(normalized.isNotBlank()) { "멱등키는 비어 있을 수 없습니다" }
            require(normalized.length <= 120) { "멱등키는 120자를 초과할 수 없습니다" }
            return normalized
        }
    }
}
