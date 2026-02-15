package com.atxbogart.trustedadvisor.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates requests that send the shared API key via X-API-Key header or Authorization: Bearer.
 * If the key matches app.auth-secret, sets an ApiKeyPrincipal in the security context.
 * When AUTH_SECRET is blank (dev), any non-empty key is accepted so login works without .env.
 */
class ApiKeyAuthenticationFilter(
    private val authSecret: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rawKey = request.getHeader("X-API-Key")
            ?: request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()
        val key = rawKey?.trim()

        if (key != null && key.isNotBlank()) {
            val secret = authSecret.trim()
            val matches = when {
                secret.isBlank() -> {
                    if (log.isWarnEnabled()) log.warn("AUTH_SECRET is not set; accepting any non-empty key (dev only)")
                    true
                }
                else -> key == secret
            }
            if (matches) {
                val principal = ApiKeyPrincipal()
                val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
