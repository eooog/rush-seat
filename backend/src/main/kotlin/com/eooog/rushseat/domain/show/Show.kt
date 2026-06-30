package com.eooog.rushseat.domain.show

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Check

@Entity
@Table(name = "show_info")
@Check(constraints = "running_minutes is null or running_minutes > 0")
class Show protected constructor() : AuditableEntity() {

    @field:Column(name = "title", nullable = false, length = 300)
    lateinit var title: String
        protected set

    @field:Column(name = "description")
    var description: String? = null
        protected set

    @field:Column(name = "running_minutes")
    var runningMinutes: Int? = null
        protected set

    fun rename(title: String) {
        this.title = validateTitle(title)
    }

    fun changeDescription(description: String?) {
        this.description = normalizeDescription(description)
    }

    fun changeRunningMinutes(runningMinutes: Int?) {
        this.runningMinutes = validateRunningMinutes(runningMinutes)
    }

    fun clearRunningMinutes() {
        this.runningMinutes = null
    }

    companion object {
        fun create(
            title: String,
            description: String? = null,
            runningMinutes: Int? = null,
        ): Show {
            return Show().apply {
                this.title = validateTitle(title)
                this.description = normalizeDescription(description)
                this.runningMinutes = validateRunningMinutes(runningMinutes)
            }
        }

        private fun validateTitle(title: String): String {
            val normalized = title.trim()
            require(normalized.isNotBlank()) { "공연 제목은 비어 있을 수 없습니다" }
            require(normalized.length <= 300) { "공연 제목은 300자를 초과할 수 없습니다" }
            return normalized
        }

        private fun normalizeDescription(description: String?): String? {
            val normalized = description?.trim()
            if (normalized.isNullOrBlank()) {
                return null
            }
            return normalized
        }

        private fun validateRunningMinutes(runningMinutes: Int?): Int? {
            if (runningMinutes == null) {
                return null
            }
            require(runningMinutes > 0) { "공연 시간은 0보다 커야 합니다" }
            return runningMinutes
        }
    }
}
