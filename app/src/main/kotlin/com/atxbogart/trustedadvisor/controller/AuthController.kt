package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    @Value("\${app.auth-debug:false}") private val authDebug: Boolean
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<MeResponse?> {
        if (authDebug) log.info("[auth] GET /api/me: principal present={}", principal != null)
        if (principal == null) return ResponseEntity.status(401).build()
        return ResponseEntity.ok(
            MeResponse(
                id = principal.userId,
                username = "api",
                displayName = null,
                profileImageUrl = null
            )
        )
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        if (authDebug) log.info("[auth] POST /api/logout")
        return ResponseEntity.noContent().build()
    }
}

data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val profileImageUrl: String?
)
