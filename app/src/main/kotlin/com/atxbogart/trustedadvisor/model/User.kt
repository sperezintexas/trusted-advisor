package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "users")
data class User(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val xId: String? = null,
    val username: String,
    val displayName: String? = null,
    val profileImageUrl: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
