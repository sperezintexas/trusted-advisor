package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class AccessRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

@Document(collection = "accessRequests")
data class AccessRequest(
    @Id
    val id: String? = null,
    @Indexed
    val email: String,
    val displayName: String? = null,
    val reason: String? = null,
    val status: AccessRequestStatus = AccessRequestStatus.PENDING,
    val oauthProvider: String? = null,
    val profileImageUrl: String? = null,
    val reviewedBy: String? = null,
    val reviewNote: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val reviewedAt: LocalDateTime? = null
)
