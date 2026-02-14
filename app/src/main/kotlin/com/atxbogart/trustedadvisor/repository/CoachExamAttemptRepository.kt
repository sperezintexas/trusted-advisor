package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachExamAttempt
import com.atxbogart.trustedadvisor.model.ExamCode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachExamAttemptRepository : MongoRepository<CoachExamAttempt, String> {
    fun findByUserIdOrderByCompletedAtDesc(userId: String): List<CoachExamAttempt>
    fun findByUserIdAndExamCodeOrderByCompletedAtDesc(userId: String, examCode: ExamCode): List<CoachExamAttempt>
}
