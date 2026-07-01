package com.eooog.rushseat.domain

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.proxy.HibernateProxy

@MappedSuperclass
abstract class BaseEntity {

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null

    override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (other === null) return false

        if (effectiveClass(this) != effectiveClass(other)) return false

        other as BaseEntity

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return effectiveClass(this).hashCode()
    }

    private fun effectiveClass(target: Any): Class<*> {
        return if (target is HibernateProxy) {
            target.hibernateLazyInitializer.persistentClass
        } else {
            target.javaClass
        }
    }

}