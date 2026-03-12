package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/api")
class AuthController(
    @Value("\${app.auth-debug:false}") private val authDebug: Boolean,
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<MeResponse?> {
        if (authDebug) log.info("[auth] GET /api/me: principal present={}", principal != null)
        val effectiveUserId = principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else return ResponseEntity.status(401).build()
        return ResponseEntity.ok(
            MeResponse(
                id = effectiveUserId,
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

    /**
     * Current auth session derived from principal + users collection.
     * - When skip-auth is enabled, always returns allowed + needsRegistration=false for a synthetic dev user.
     * - When auth is enforced, looks up the user by principal userId (treated as email) in Mongo.
     */
    @GetMapping("/auth/session")
    fun authSession(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<AuthSessionResponse> {
        if (skipAuth) {
            return ResponseEntity.ok(
                AuthSessionResponse(
                    allowed = true,
                    needsRegistration = false,
                    user = AuthUserView(
                        email = "dev-user@example.com",
                        username = "dev-user",
                        displayName = "Developer"
                    )
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val user = userRepository.findByEmail(email)
            ?: return ResponseEntity.ok(
                AuthSessionResponse(
                    allowed = false,
                    needsRegistration = false,
                    user = null
                )
            )
        val needsRegistration = !user.registered
        return ResponseEntity.ok(
            AuthSessionResponse(
                allowed = true,
                needsRegistration = needsRegistration,
                user = AuthUserView(
                    email = user.email ?: email,
                    username = user.username,
                    displayName = user.displayName
                )
            )
        )
    }

    @PostMapping("/auth/register")
    fun register(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestBody body: RegistrationRequest
    ): ResponseEntity<AuthSessionResponse> {
        if (skipAuth) {
            // In dev mode just echo a synthetic user.
            return ResponseEntity.ok(
                AuthSessionResponse(
                    allowed = true,
                    needsRegistration = false,
                    user = AuthUserView(
                        email = "dev-user@example.com",
                        username = body.username.ifBlank { "dev-user" },
                        displayName = body.displayName.ifBlank { "Developer" }
                    )
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val existing = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(403).build()

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated: User = existing.copy(
            username = if (body.username.isNotBlank()) body.username else existing.username,
            displayName = body.displayName.ifBlank { existing.displayName },
            registered = true,
            firstLoginAt = existing.firstLoginAt ?: now,
            lastLoginAt = now,
            updatedAt = now
        )
        val saved = userRepository.save(updated)
        return ResponseEntity.ok(
            AuthSessionResponse(
                allowed = true,
                needsRegistration = false,
                user = AuthUserView(
                    email = saved.email ?: email,
                    username = saved.username,
                    displayName = saved.displayName
                )
            )
        )
    }

    private fun currentEmailFromOAuth2(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal as? OAuth2User ?: return null
        val attrs = principal.attributes
        return (attrs["email"] as? String)
            ?: (attrs["login"] as? String)?.let { "$it@github.local" }
    }
}

data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val profileImageUrl: String?
)

data class AuthUserView(
    val email: String,
    val username: String,
    val displayName: String?
)

data class AuthSessionResponse(
    val allowed: Boolean,
    val needsRegistration: Boolean,
    val user: AuthUserView?
)

data class RegistrationRequest(
    val username: String = "",
    val displayName: String = ""
)
