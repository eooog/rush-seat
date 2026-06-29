package com.eooog.rushseat.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalErrorHandler {
    @ExceptionHandler(ApiError::class)
    fun handleApiError(error: ApiError): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(error.status)
            .body(ErrorResponse(code = error.code, message = error.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(error: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = error.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Invalid request"
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(error: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(code = "INTERNAL_SERVER_ERROR", message = "Unexpected server error"))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
)
