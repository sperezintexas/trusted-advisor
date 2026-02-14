package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachUserProgress")
data class CoachUserProgress(
    @Id
    val id: String? = null,
    val userId: String,
    val examCode: ExamCode,
    val totalAsked: Int = 0,
    val correct: Int = 0,
    val lastSessionAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val weakTopics: List<WeakTopic> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)

data class WeakTopic(
    val topic: String,
    val missCount: Int
)
