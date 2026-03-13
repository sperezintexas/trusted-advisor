package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "userTokenUsageDaily")
@CompoundIndex(name = "user_date_idx", def = "{'userId': 1, 'date': 1}", unique = true)
data class UserTokenUsageDaily(
    @Id
    val id: String? = null,
    val userId: String,
    /** UTC date key in YYYY-MM-DD format. */
    val date: String,
    val tokens: Long = 0,
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
