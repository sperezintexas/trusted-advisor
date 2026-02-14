package com.atxbogart.trustedadvisor.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<Unit> {
        log.debug("No static resource: {}", e.resourcePath)
        return ResponseEntity.notFound().build()
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccess(e: DataAccessException): ResponseEntity<ErrorResponse> {
        log.warn("Data access error: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("Database unavailable. Check that MongoDB is running and MONGODB_URI is correct."))
    }

    @ExceptionHandler(WebClientResponseException::class)
    fun handleWebClient(e: WebClientResponseException): ResponseEntity<ErrorResponse> {
        log.warn("Upstream API error: {} {}", e.statusCode, e.responseBodyAsString)
        val message = parseUpstreamError(e.responseBodyAsString)
            ?: "AI service error (${e.statusCode}). Check XAI_API_KEY and backend logs."
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(message))
    }

    @ExceptionHandler(Exception::class)
    fun handleOther(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled error", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("Request failed. Check backend logs for details."))
    }

    private fun parseUpstreamError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val tree = objectMapper.readTree(body)
            tree.get("error")?.get("message")?.asText()
                ?: tree.get("message")?.asText()
        } catch (_: Exception) {
            null
        }
    }
}

data class ErrorResponse(val message: String)
