package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.RecommendationStatus
import com.atxbogart.trustedadvisor.repository.CoachExamAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class CoachRecommendationQueueService(
    private val coachExamAttemptRepository: CoachExamAttemptRepository,
    private val coachLearningPlanService: CoachLearningPlanService,
    private val coachService: CoachService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000L)
    fun processQueuedRecommendations() {
        val queued = coachExamAttemptRepository.findTop10ByRecommendationStatusOrderByCompletedAtAsc(
            RecommendationStatus.QUEUED
        )
        if (queued.isEmpty()) return

        queued.forEach { attempt ->
            val attemptId = attempt.id ?: return@forEach
            val processing = attempt.copy(
                recommendationStatus = RecommendationStatus.PROCESSING,
                recommendationAttempts = attempt.recommendationAttempts + 1,
                recommendationUpdatedAt = LocalDateTime.now(ZoneOffset.UTC)
            )
            coachExamAttemptRepository.save(processing)

            try {
                val recommendation = coachLearningPlanService.buildRecommendation(
                    examCode = attempt.examCode,
                    correct = attempt.correct,
                    total = attempt.total,
                    percentage = attempt.percentage,
                    passingPercentage = coachService.getPassingPercentage(attempt.examCode),
                    missedTopics = attempt.missedTopics
                )
                coachExamAttemptRepository.save(
                    processing.copy(
                        recommendation = recommendation,
                        recommendationStatus = RecommendationStatus.READY,
                        recommendationError = null,
                        recommendationUpdatedAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                )
            } catch (e: Exception) {
                log.warn("[coach-queue] recommendation failed for attempt {}: {}", attemptId, e.message)
                coachExamAttemptRepository.save(
                    processing.copy(
                        recommendationStatus = RecommendationStatus.FAILED,
                        recommendationError = e.message,
                        recommendationUpdatedAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                )
            }
        }
    }
}
