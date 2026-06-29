package com.eooog.rushseat.queue

import com.eooog.rushseat.common.ApiError
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class QueueService(
    private val redis: StringRedisTemplate,
    @Value("\${rush-seat.queue.token-ttl-seconds}") private val queueTokenTtlSeconds: Long,
    @Value("\${rush-seat.queue.admission-token-ttl-seconds}") private val admissionTokenTtlSeconds: Long,
) {
    fun enter(eventId: Long, request: QueueEnterRequest): QueueEnterResponse {
        val waitingKey = waitingKey(eventId)
        val nowMillis = Instant.now().toEpochMilli().toDouble()
        redis.opsForZSet().add(waitingKey, request.userId, nowMillis)

        val queueToken = "qt_${UUID.randomUUID()}"
        val tokenKey = queueTokenKey(queueToken)
        redis.opsForHash<String, String>().putAll(
            tokenKey,
            mapOf(
                "eventId" to eventId.toString(),
                "userId" to request.userId,
                "status" to "WAITING",
            ),
        )
        redis.expire(tokenKey, Duration.ofSeconds(queueTokenTtlSeconds))
        redis.opsForValue().set(userQueueTokenKey(eventId, request.userId), queueToken, Duration.ofSeconds(queueTokenTtlSeconds))

        val rank = redis.opsForZSet().rank(waitingKey, request.userId)?.plus(1)
        return QueueEnterResponse(
            status = "WAITING",
            queueToken = queueToken,
            rank = rank,
            estimatedWaitSeconds = estimateWaitSeconds(rank),
        )
    }

    fun getStatus(eventId: Long, queueToken: String): QueueStatusResponse {
        val token = readQueueToken(queueToken)
        if (token.eventId != eventId) {
            throw ApiError("QUEUE_TOKEN_EVENT_MISMATCH", "Queue token does not belong to this event", HttpStatus.FORBIDDEN)
        }

        val hash = redis.opsForHash<String, String>()
        val admissionToken = hash.get(queueTokenKey(queueToken), "admissionToken")
        val admissionExpiresAt = hash.get(queueTokenKey(queueToken), "admissionExpiresAt")?.let(Instant::parse)
        if (admissionToken != null) {
            return QueueStatusResponse(
                status = "ADMITTED",
                rank = null,
                estimatedWaitSeconds = null,
                admissionToken = admissionToken,
                expiresAt = admissionExpiresAt,
            )
        }

        val rank = redis.opsForZSet().rank(waitingKey(eventId), token.userId)?.plus(1)
        return QueueStatusResponse(
            status = token.status,
            rank = rank,
            estimatedWaitSeconds = estimateWaitSeconds(rank),
            admissionToken = null,
            expiresAt = null,
        )
    }

    fun admitNext(eventId: Long, limit: Int): AdmitResponse {
        val normalizedLimit = limit.coerceIn(1, 1_000)
        val popped = redis.opsForZSet().popMin(waitingKey(eventId), normalizedLimit.toLong()).orEmpty()
        val expiresAt = Instant.now().plusSeconds(admissionTokenTtlSeconds)

        val admissions = popped.mapNotNull { tuple ->
            val userId = tuple.value ?: return@mapNotNull null
            val admissionToken = "at_${UUID.randomUUID()}"
            val admissionKey = admissionTokenKey(admissionToken)
            redis.opsForHash<String, String>().putAll(
                admissionKey,
                mapOf(
                    "eventId" to eventId.toString(),
                    "userId" to userId,
                ),
            )
            redis.expire(admissionKey, Duration.ofSeconds(admissionTokenTtlSeconds))

            redis.opsForValue().get(userQueueTokenKey(eventId, userId))?.let { queueToken ->
                redis.opsForHash<String, String>().putAll(
                    queueTokenKey(queueToken),
                    mapOf(
                        "status" to "ADMITTED",
                        "admissionToken" to admissionToken,
                        "admissionExpiresAt" to expiresAt.toString(),
                    ),
                )
            }

            AdmissionDto(userId = userId, admissionToken = admissionToken, expiresAt = expiresAt)
        }

        return AdmitResponse(admittedCount = admissions.size, admissions = admissions)
    }

    fun requireAdmitted(authorization: String?, eventId: Long): AdmittedUser {
        val token = authorization
            ?.removePrefix("Bearer ")
            ?.takeIf { it.startsWith("at_") }
            ?: throw ApiError("ADMISSION_REQUIRED", "Admission token is required", HttpStatus.UNAUTHORIZED)

        val values = redis.opsForHash<String, String>().entries(admissionTokenKey(token))
        if (values.isEmpty()) {
            throw ApiError("ADMISSION_EXPIRED", "Admission token is invalid or expired", HttpStatus.UNAUTHORIZED)
        }

        val tokenEventId = values["eventId"]?.toLongOrNull()
        val userId = values["userId"]
        if (tokenEventId != eventId || userId == null) {
            throw ApiError("ADMISSION_EVENT_MISMATCH", "Admission token does not belong to this event", HttpStatus.FORBIDDEN)
        }

        return AdmittedUser(eventId = eventId, userId = userId, admissionToken = token)
    }

    private fun readQueueToken(queueToken: String): QueueTokenValue {
        val values = redis.opsForHash<String, String>().entries(queueTokenKey(queueToken))
        if (values.isEmpty()) {
            throw ApiError("QUEUE_TOKEN_EXPIRED", "Queue token is invalid or expired", HttpStatus.UNAUTHORIZED)
        }
        return QueueTokenValue(
            eventId = values["eventId"]?.toLongOrNull()
                ?: throw ApiError("QUEUE_TOKEN_INVALID", "Queue token is invalid", HttpStatus.UNAUTHORIZED),
            userId = values["userId"] ?: throw ApiError("QUEUE_TOKEN_INVALID", "Queue token is invalid", HttpStatus.UNAUTHORIZED),
            status = values["status"] ?: "WAITING",
        )
    }

    private fun estimateWaitSeconds(rank: Long?): Long? {
        if (rank == null) return null
        val assumedAdmissionPerSecond = 100L
        return rank / assumedAdmissionPerSecond
    }

    private fun waitingKey(eventId: Long) = "queue:waiting:$eventId"
    private fun queueTokenKey(queueToken: String) = "queue:token:$queueToken"
    private fun userQueueTokenKey(eventId: Long, userId: String) = "queue:user-token:$eventId:$userId"
    private fun admissionTokenKey(admissionToken: String) = "admission:token:$admissionToken"

    private data class QueueTokenValue(
        val eventId: Long,
        val userId: String,
        val status: String,
    )
}
