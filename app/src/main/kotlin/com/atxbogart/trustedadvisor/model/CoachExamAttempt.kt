package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachExamAttempts")
data class CoachExamAttempt(
    @Id
    val id: String? = null,
    val userId: String,
    val examCode: ExamCode,
    val correct: Int,
    val total: Int,
    val percentage: Double,
    val passed: Boolean,
    val completedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
