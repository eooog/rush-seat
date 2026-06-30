package com.eooog.rushseat.domain.show

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
    name = "show_performer",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_show_performer",
            columnNames = ["show_id", "performer_id"]
        )
    ]
)
@Check(constraints = "display_order >= 0")
class ShowPerformer protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "show_id", nullable = false)
    lateinit var show: Show
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "performer_id", nullable = false)
    lateinit var performer: Performer
        protected set

    @field:Column(name = "role", length = 100)
    var role: String? = null
        protected set

    @field:Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0
        protected set

    fun changeRole(role: String?) {
        this.role = normalizeRole(role)
    }

    fun changeDisplayOrder(displayOrder: Int) {
        this.displayOrder = validateDisplayOrder(displayOrder)
    }

    companion object {
        fun create(
            show: Show,
            performer: Performer,
            role: String? = null,
            displayOrder: Int = 0,
        ): ShowPerformer {
            return ShowPerformer().apply {
                this.show = show
                this.performer = performer
                this.role = normalizeRole(role)
                this.displayOrder = validateDisplayOrder(displayOrder)
            }
        }

        private fun normalizeRole(role: String?): String? {
            val normalized = role?.trim()
            if (normalized.isNullOrBlank()) {
                return null
            }
            require(normalized.length <= 100) { "출연자 역할은 100자를 초과할 수 없습니다" }
            return normalized
        }

        private fun validateDisplayOrder(displayOrder: Int): Int {
            require(displayOrder >= 0) { "출연자 표시 순서는 0 이상이어야 합니다" }
            return displayOrder
        }
    }
}
