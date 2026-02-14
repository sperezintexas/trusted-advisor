package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachExam
import com.atxbogart.trustedadvisor.model.ExamCode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachExamRepository : MongoRepository<CoachExam, String> {
    fun findByCode(code: ExamCode): CoachExam?
}
