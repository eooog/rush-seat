package com.eooog.rushseat.domain.venue

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(
    name = "venue",
)
class Venue protected constructor() : AuditableEntity() {

    @field:Column(name = "name", nullable = false, length = 200)
    lateinit var name: String
        protected set

    @field:Column(name = "address", length = 500)
    var address: String? = null
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    companion object {
        fun create(
            name: String,
            address: String? = null,
        ): Venue {
            return Venue().apply {
                this.name = validateName(name)
                this.address = normalizeAddress(address)
            }
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()

            require(normalized.isNotBlank()) {
                "공연장 이름은 비어 있을 수 없습니다"
            }

            require(normalized.length <= 200) {
                "공연장 이름은 200자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun normalizeAddress(address: String?): String? {
            val normalized = address?.trim()

            if (normalized.isNullOrBlank()) {
                return null
            }

            require(normalized.length <= 500) {
                "공연장 주소는 500자를 초과할 수 없습니다"
            }

            return normalized
        }
    }
}
