package com.eooog.rushseat.realtime

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketSessionRegistry {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tileSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionTiles = ConcurrentHashMap<String, MutableSet<String>>()

    fun subscribe(eventId: Long, tileId: String, session: WebSocketSession) {
        val key = tileKey(eventId, tileId)
        tileSessions.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(session)
        sessionTiles.computeIfAbsent(session.id) { ConcurrentHashMap.newKeySet() }.add(key)
    }

    fun remove(session: WebSocketSession) {
        val keys = sessionTiles.remove(session.id).orEmpty()
        keys.forEach { key ->
            tileSessions[key]?.remove(session)
        }
    }

    fun sendToTile(eventId: Long, tileId: String, payload: String) {
        val sessions = tileSessions[tileKey(eventId, tileId)].orEmpty()
        sessions.forEach { session ->
            if (!session.isOpen) {
                remove(session)
                return@forEach
            }
            try {
                synchronized(session) {
                    if (session.isOpen) {
                        session.sendMessage(TextMessage(payload))
                    }
                }
            } catch (e: Exception) {
                log.debug("Failed to send WebSocket message. sessionId={}", session.id, e)
                remove(session)
            }
        }
    }

    private fun tileKey(eventId: Long, tileId: String): String = "$eventId:$tileId"
}
