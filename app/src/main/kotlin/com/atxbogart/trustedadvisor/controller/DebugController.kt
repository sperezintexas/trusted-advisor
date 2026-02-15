package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/debug")
class DebugController(
    @Value("\${app.auth-secret:}") private val authSecret: String,
    @Value("\${app.auth-debug:false}") private val authDebug: Boolean
) {

    @GetMapping("/auth")
    fun auth(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<AuthDebugResponse> {
        val apiKeyConfigured = authSecret.isNotBlank()
        return ResponseEntity.ok(
            AuthDebugResponse(
                apiKeyConfigured = apiKeyConfigured,
                authDebugEnabled = authDebug,
                userId = principal?.userId,
                username = principal?.let { "api" }
            )
        )
    }
}

data class AuthDebugResponse(
    val apiKeyConfigured: Boolean,
    val authDebugEnabled: Boolean,
    val userId: String?,
    val username: String?
)
