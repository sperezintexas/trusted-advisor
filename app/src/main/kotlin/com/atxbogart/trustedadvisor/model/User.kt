package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class UserRole {
    ADMIN,
    BASIC,
    PREMIUM
}

@Document(collection = "users")
data class User(
    @Id
    val id: String? = null,
    /**
     * Primary identifier for end-users. Pre-provisioned emails represent allowed users.
     * Nullable for legacy/seeded users; new users should always set email.
     */
    @Indexed(unique = true, sparse = true)
    val email: String? = null,
    /** Optional X (Twitter) user id for linkage. */
    @Indexed(unique = true, sparse = true)
    val xId: String? = null,
    val username: String,
    val displayName: String? = null,
    val profileImageUrl: String? = null,
    @Indexed(unique = true, sparse = true)
    val stripeCustomerId: String? = null,
    val stripeSubscriptionId: String? = null,
    val stripeSubscriptionStatus: String? = null,
    /** User role: ADMIN, BASIC (free), or PREMIUM (paid). Defaults to BASIC. */
    val role: UserRole = UserRole.BASIC,
    /** True once the user has completed in-app registration for the first time. */
    val registered: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val firstLoginAt: LocalDateTime? = null,
    val lastLoginAt: LocalDateTime? = null
)
