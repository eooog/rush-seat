package com.eooog.rushseat.domain.seatmap

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check

@Entity
@Table(
    name = "seat",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_seat_seat_map_code",
            columnNames = ["seat_map_id", "code"]
        )
    ]
)
@Check(constraints = "row_no > 0 and column_no > 0")
class Seat protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "seat_map_id", nullable = false)
    lateinit var seatMap: SeatMap
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

    @field:Column(name = "row_label", length = 50)
    var rowLabel: String? = null
        protected set

    @field:Column(name = "seat_no", length = 50)
    var seatNo: String? = null
        protected set

    @field:Column(name = "row_no", nullable = false)
    var rowNo: Int = 0
        protected set

    @field:Column(name = "column_no", nullable = false)
    var columnNo: Int = 0
        protected set

    fun moveTo(rowNo: Int, columnNo: Int) {
        val validatedRowNo = validateNo("rowNo", rowNo)
        val validatedColumnNo = validateNo("columnNo", columnNo)

        check(tile.contains(validatedRowNo, validatedColumnNo)) {
            "좌석 위치는 타일 범위 안에 있어야 합니다"
        }

        this.rowNo = validatedRowNo
        this.columnNo = validatedColumnNo
    }

    fun changeDisplayLabel(rowLabel: String?, seatNo: String?) {
        this.rowLabel = normalizeNullableText("좌석 행", rowLabel, 50)
        this.seatNo = normalizeNullableText("좌석 번호", seatNo, 50)
    }

    companion object {
        fun create(
            seatMap: SeatMap,
            sector: Sector,
            tile: Tile,
            code: String,
            rowLabel: String? = null,
            seatNo: String? = null,
            rowNo: Int,
            columnNo: Int,
        ): Seat {
            require(sector.seatMap == seatMap) {
                "좌석의 구역은 동일한 좌석 배치에 속해야 합니다"
            }

            require(tile.seatMap == seatMap) {
                "좌석의 타일은 동일한 좌석 배치에 속해야 합니다"
            }

            require(tile.sector == sector) {
                "좌석의 타일은 동일한 구역에 속해야 합니다"
            }

            val validatedRowNo = validateNo("rowNo", rowNo)
            val validatedColumnNo = validateNo("columnNo", columnNo)

            require(tile.contains(validatedRowNo, validatedColumnNo)) {
                "좌석 위치는 타일 범위 안에 있어야 합니다"
            }

            return Seat().apply {
                this.seatMap = seatMap
                this.sector = sector
                this.tile = tile
                this.code = validateCode(code)
                this.rowLabel = normalizeNullableText("좌석 행", rowLabel, 50)
                this.seatNo = normalizeNullableText("좌석 번호", seatNo, 50)
                this.rowNo = validatedRowNo
                this.columnNo = validatedColumnNo
            }
        }

        private fun validateCode(code: String): String {
            val normalized = code.trim()
            require(normalized.isNotBlank()) { "좌석 코드는 비어 있을 수 없습니다" }
            require(normalized.length <= 100) { "좌석 코드는 100자를 초과할 수 없습니다" }
            return normalized
        }

        private fun normalizeNullableText(label: String, value: String?, maxLength: Int): String? {
            val normalized = value?.trim()

            if (normalized.isNullOrBlank()) {
                return null
            }

            require(normalized.length <= maxLength) {
                "$label 값은 ${maxLength}자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateNo(name: String, value: Int): Int {
            require(value > 0) {
                "$name 값은 0보다 커야 합니다"
            }

            return value
        }
    }
}
