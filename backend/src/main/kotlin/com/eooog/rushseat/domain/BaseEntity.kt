package com.eooog.rushseat.domain

import jakarta.persistence.MappedSuperclass
import org.hibernate.proxy.HibernateProxy

@MappedSuperclass
abstract class BaseEntity {

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