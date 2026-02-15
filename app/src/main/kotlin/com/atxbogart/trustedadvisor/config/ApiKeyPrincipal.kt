package com.atxbogart.trustedadvisor.config

/**
 * Principal for requests authenticated via API key (AUTH_SECRET).
 * Used when X OAuth2 is disabled; all such requests share a single synthetic user id.
 */
data class ApiKeyPrincipal(val userId: String = "api-user") : java.security.Principal {
    override fun getName(): String = userId
}
