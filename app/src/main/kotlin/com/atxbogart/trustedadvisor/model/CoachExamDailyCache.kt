package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachExamDailyCache")
data class CoachExamDailyCache(
    @Id
    val id: String,
    val examCode: ExamCode,
    val cacheDate: String,
    val questionIds: List<String>,
    val generatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
