package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachQuestion
import com.atxbogart.trustedadvisor.model.ExamCode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachQuestionRepository : MongoRepository<CoachQuestion, String> {
    fun findByExamCodeAndActiveTrue(examCode: ExamCode): List<CoachQuestion>
}
