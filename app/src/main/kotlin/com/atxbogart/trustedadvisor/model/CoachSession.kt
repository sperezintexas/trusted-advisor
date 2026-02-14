package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachSessions")
data class CoachSession(
    @Id
    val id: String? = null,
    val userId: String,
    val examCode: ExamCode,
    val numQuestions: Int,
    val questionsIds: List<String>,
    val currentIndex: Int = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val answers: Map<String, ChoiceLetter> = emptyMap(),
    val score: Int? = null,
    val weakTopics: List<WeakTopic> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)