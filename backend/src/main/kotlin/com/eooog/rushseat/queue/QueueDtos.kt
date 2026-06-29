package com.eooog.rushseat.queue

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class QueueEnterRequest(
    @field:NotBlank
    val userId: String,
)

data class QueueEnterResponse(
    val status: String,
    val queueToken: String,
    val rank: Long?,
    val estimatedWaitSeconds: Long?,
)

data class QueueStatusResponse(
    val status: String,
    val rank: Long?,
    val estimatedWaitSeconds: Long?,
    val admissionToken: String?,
    val expiresAt: Instant?,
)

data class AdmitResponse(
    val admittedCount: Int,
    val admissions: List<AdmissionDto>,
)

data class AdmissionDto(
    val userId: String,
    val admissionToken: String,
    val expiresAt: Instant,
)

data class AdmittedUser(
    val eventId: Long,
    val userId: String,
    val admissionToken: String,
)
