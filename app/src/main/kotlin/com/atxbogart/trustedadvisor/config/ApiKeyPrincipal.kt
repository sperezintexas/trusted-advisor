package com.atxbogart.trustedadvisor.config

/**
 * Principal for requests authenticated via API key (AUTH_SECRET).
 * Used when X OAuth2 is disabled; accepts userId from X-User-Id header.
 */
data class ApiKeyPrincipal(val userId: String) : java.security.Principal {
    override fun getName(): String = userId
}