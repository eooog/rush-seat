package com.eooog.rushseat.domain.performance

import com.eooog.rushseat.domain.AuditableEntity
import com.eooog.rushseat.domain.seatmap.SeatMap
import com.eooog.rushseat.domain.show.Show
import com.eooog.rushseat.domain.venue.Hall
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Check
import java.time.Instant

@Entity
@Table(name = "performance")
@Check(constraints = "starts_at < ends_at and sales_open_at < sales_close_at")
class Performance protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "show_id", nullable = false)
    lateinit var show: Show
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "hall_id", nullable = false)
    lateinit var hall: Hall
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "seat_map_id", nullable = false)
    lateinit var seatMap: SeatMap
        protected set

    @field:Column(name = "starts_at", nullable = false)
    lateinit var startsAt: Instant
        protected set

    @field:Column(name = "ends_at", nullable = false)
    lateinit var endsAt: Instant
        protected set

    @field:Column(name = "sales_open_at", nullable = false)
    lateinit var salesOpenAt: Instant
        protected set

    @field:Column(name = "sales_close_at", nullable = false)
    lateinit var salesCloseAt: Instant
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 30)
    var status: PerformanceStatus = PerformanceStatus.SCHEDULED
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "sales_status", nullable = false, length = 30)
    var salesStatus: PerformanceSalesStatus = PerformanceSalesStatus.BEFORE_SALE
        protected set

    fun reschedule(startsAt: Instant, endsAt: Instant) {
        assertEditable()
        validatePerformanceTime(startsAt, endsAt)
        this.startsAt = startsAt
        this.endsAt = endsAt
    }

    fun changeSalesPeriod(salesOpenAt: Instant, salesCloseAt: Instant) {
        assertEditable()
        validateSalesPeriod(salesOpenAt, salesCloseAt)
        this.salesOpenAt = salesOpenAt
        this.salesCloseAt = salesCloseAt
    }

    fun openSales() {
        assertEditable()
        check(salesStatus == PerformanceSalesStatus.BEFORE_SALE) {
            "예매 시작 전 상태의 공연 회차만 판매를 시작할 수 있습니다"
        }
        this.salesStatus = PerformanceSalesStatus.ON_SALE
    }

    fun closeSales() {
        assertEditable()
        check(salesStatus == PerformanceSalesStatus.ON_SALE) {
            "판매 중인 공연 회차만 판매를 종료할 수 있습니다"
        }
        this.salesStatus = PerformanceSalesStatus.CLOSED
    }

    fun complete(now: Instant) {
        check(status == PerformanceStatus.SCHEDULED) {
            "예정 상태의 공연 회차만 종료 처리할 수 있습니다"
        }
        check(!endsAt.isAfter(now)) {
            "예정 종료 시각 이후에만 공연 회차를 종료 처리할 수 있습니다"
        }

        this.status = PerformanceStatus.COMPLETED
        this.salesStatus = PerformanceSalesStatus.CLOSED
    }

    fun cancel() {
        check(status != PerformanceStatus.CANCELLED) {
            "이미 취소된 공연 회차입니다"
        }
        check(status != PerformanceStatus.COMPLETED) {
            "종료된 공연 회차는 취소할 수 없습니다"
        }

        this.status = PerformanceStatus.CANCELLED
        this.salesStatus = PerformanceSalesStatus.CLOSED
    }

    fun assertOnSale() {
        check(status == PerformanceStatus.SCHEDULED && salesStatus == PerformanceSalesStatus.ON_SALE) {
            "판매 중인 공연 회차가 아닙니다"
        }
    }

    private fun assertEditable() {
        check(status == PerformanceStatus.SCHEDULED) {
            "예정 상태의 공연 회차만 변경할 수 있습니다"
        }
    }

    companion object {
        fun create(
            show: Show,
            hall: Hall,
            seatMap: SeatMap,
            startsAt: Instant,
            endsAt: Instant,
            salesOpenAt: Instant,
            salesCloseAt: Instant,
            status: PerformanceStatus = PerformanceStatus.SCHEDULED,
            salesStatus: PerformanceSalesStatus = PerformanceSalesStatus.BEFORE_SALE,
        ): Performance {
            require(seatMap.hall == hall) {
                "공연 회차의 좌석 배치도는 동일한 홀에 속해야 합니다"
            }
            seatMap.assertActive()
            validatePerformanceTime(startsAt, endsAt)
            validateSalesPeriod(salesOpenAt, salesCloseAt)

            return Performance().apply {
                this.show = show
                this.hall = hall
                this.seatMap = seatMap
                this.startsAt = startsAt
                this.endsAt = endsAt
                this.salesOpenAt = salesOpenAt
                this.salesCloseAt = salesCloseAt
                this.status = status
                this.salesStatus = salesStatus
            }
        }

        private fun validatePerformanceTime(startsAt: Instant, endsAt: Instant) {
            require(startsAt < endsAt) {
                "공연 종료 시각은 시작 시각보다 이후여야 합니다"
            }
        }

        private fun validateSalesPeriod(salesOpenAt: Instant, salesCloseAt: Instant) {
            require(salesOpenAt < salesCloseAt) {
                "예매 종료 시각은 예매 시작 시각보다 이후여야 합니다"
            }
        }
    }
}
