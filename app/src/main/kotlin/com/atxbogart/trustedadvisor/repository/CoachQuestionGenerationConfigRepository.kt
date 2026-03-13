package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachQuestionGenerationConfig
import com.atxbogart.trustedadvisor.model.ExamCode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachQuestionGenerationConfigRepository : MongoRepository<CoachQuestionGenerationConfig, String> {
    fun findByExamCode(examCode: ExamCode): CoachQuestionGenerationConfig?
}
