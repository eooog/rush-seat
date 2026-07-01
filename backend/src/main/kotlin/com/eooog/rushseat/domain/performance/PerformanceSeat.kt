package com.eooog.rushseat.domain.performance

import com.eooog.rushseat.domain.AuditableEntity
import com.eooog.rushseat.domain.seatmap.Seat
import com.eooog.rushseat.domain.seatmap.Sector
import com.eooog.rushseat.domain.seatmap.Tile
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(
    name = "performance_seat",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_performance_seat",
            columnNames = ["performance_id", "seat_id"]
        )
    ]
)
class PerformanceSeat protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "performance_id", nullable = false)
    lateinit var performance: Performance
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "seat_id", nullable = false)
    lateinit var seat: Seat
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "sector_id", nullable = false)
    lateinit var sector: Sector
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "tile_id", nullable = false)
    lateinit var tile: Tile
        protected set

    @field:Column(name = "code", nullable = false, length = 100)
    lateinit var code: String
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 30)
    var status: PerformanceSeatStatus = PerformanceSeatStatus.AVAILABLE
        protected set

    @field:Column(name = "hold_owner_id", length = 100)
    var holdOwnerId: String? = null
        protected set

    @field:Column(name = "hold_token", length = 120)
    var holdToken: String? = null
        protected set

    @field:Column(name = "hold_expires_at")
    var holdExpiresAt: Instant? = null
        protected set

    @field:Version
    @field:Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    fun markClaiming() {
        check(status == PerformanceSeatStatus.AVAILABLE) {
            "선택 가능한 좌석만 선택 시도 상태로 변경할 수 있습니다"
        }
        this.status = PerformanceSeatStatus.CLAIMING
    }

    fun hold(ownerId: String, token: String, expiresAt: Instant) {
        check(status == PerformanceSeatStatus.AVAILABLE || status == PerformanceSeatStatus.CLAIMING) {
            "선택 가능하거나 선택 시도 중인 좌석만 선점할 수 있습니다"
        }

        this.status = PerformanceSeatStatus.HELD
        this.holdOwnerId = validateOwnerId(ownerId)
        this.holdToken = validateHoldToken(token)
        this.holdExpiresAt = expiresAt
    }

    fun reserve(token: String) {
        check(status == PerformanceSeatStatus.HELD) {
            "선점된 좌석만 예약 확정할 수 있습니다"
        }
        check(holdToken == token) {
            "좌석 선점 토큰이 일치하지 않습니다"
        }

        this.status = PerformanceSeatStatus.RESERVED
        clearHold()
    }

    fun releaseHold() {
        check(status == PerformanceSeatStatus.HELD || status == PerformanceSeatStatus.CLAIMING) {
            "선점 또는 선택 시도 상태의 좌석만 해제할 수 있습니다"
        }

        this.status = PerformanceSeatStatus.AVAILABLE
        clearHold()
    }

    private fun clearHold() {
        this.holdOwnerId = null
        this.holdToken = null
        this.holdExpiresAt = null
    }

    companion object {
        fun create(
            performance: Performance,
            seat: Seat,
            status: PerformanceSeatStatus = PerformanceSeatStatus.AVAILABLE,
        ): PerformanceSeat {
            require(seat.seatMap == performance.seatMap) {
                "공연 회차 좌석은 동일한 좌석 배치도에 속해야 합니다"
            }

            return PerformanceSeat().apply {
                this.performance = performance
                this.seat = seat
                this.sector = seat.sector
                this.tile = seat.tile
                this.code = seat.code
                this.status = status
                this.version = 0
            }
        }

        private fun validateOwnerId(ownerId: String): String {
            val normalized = ownerId.trim()
            require(normalized.isNotBlank()) { "좌석 선점 사용자 ID는 비어 있을 수 없습니다" }
            require(normalized.length <= 100) { "좌석 선점 사용자 ID는 100자를 초과할 수 없습니다" }
            return normalized
        }

        private fun validateHoldToken(token: String): String {
            val normalized = token.trim()
            require(normalized.isNotBlank()) { "좌석 선점 토큰은 비어 있을 수 없습니다" }
            require(normalized.length <= 120) { "좌석 선점 토큰은 120자를 초과할 수 없습니다" }
            return normalized
        }
    }
}
