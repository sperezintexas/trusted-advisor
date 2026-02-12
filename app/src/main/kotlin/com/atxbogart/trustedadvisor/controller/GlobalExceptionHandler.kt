package com.atxbogart.trustedadvisor.controller

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccess(e: DataAccessException): ResponseEntity<ErrorResponse> {
        log.warn("Data access error: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("Database unavailable. Check that MongoDB is running and MONGODB_URI is correct."))
    }

    @ExceptionHandler(Exception::class)
    fun handleOther(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled error", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("Request failed. Check backend logs for details."))
    }
}

data class ErrorResponse(val message: String)
