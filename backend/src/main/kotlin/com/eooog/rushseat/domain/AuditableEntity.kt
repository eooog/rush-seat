package com.eooog.rushseat.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditableEntity : BaseEntity() {

    @field:CreatedDate
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @field:LastModifiedDate
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

}