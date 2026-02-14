package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachExams")
data class CoachExam(
    @Id
    val id: String? = null,
    val code: ExamCode,
    val name: String,
    val version: String,
    val totalQuestionsInOutline: Int,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)

enum class ExamCode {
    SIE,
    SERIES_7,
    SERIES_57
}
