package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachQuestionGenerationConfigs")
data class CoachQuestionGenerationConfig(
    @Id
    val id: String,
    val examCode: ExamCode,
    val enabled: Boolean = false,
    val personaId: String = "finance-coach",
    val targetPoolSize: Int,
    val intervalMinutes: Int = 60,
    val chunkSize: Int = 25,
    val nextRunAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val lastRunAt: LocalDateTime? = null,
    val running: Boolean = false,
    val lastStatus: String? = null,
    val lastMessage: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
