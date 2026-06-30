package com.eooog.rushseat.domain.show

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "performer")
class Performer protected constructor() : AuditableEntity() {

    @field:Column(name = "name", nullable = false, length = 200)
    lateinit var name: String
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    companion object {
        fun create(name: String): Performer {
            return Performer().apply {
                this.name = validateName(name)
            }
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()
            require(normalized.isNotBlank()) { "출연자 이름은 비어 있을 수 없습니다" }
            require(normalized.length <= 200) { "출연자 이름은 200자를 초과할 수 없습니다" }
            return normalized
        }
    }
}
