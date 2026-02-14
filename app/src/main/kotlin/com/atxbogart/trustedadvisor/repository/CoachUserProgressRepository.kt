package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachUserProgress
import com.atxbogart.trustedadvisor.model.ExamCode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachUserProgressRepository : MongoRepository<CoachUserProgress, String> {
    fun findByUserIdAndExamCode(userId: String, examCode: ExamCode): CoachUserProgress?
}
