package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachExamDailyCache
import com.atxbogart.trustedadvisor.model.ExamCode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachExamDailyCacheRepository : MongoRepository<CoachExamDailyCache, String> {
    fun findByExamCodeAndCacheDate(examCode: ExamCode, cacheDate: String): CoachExamDailyCache?
    fun findTop1ByExamCodeOrderByGeneratedAtDesc(examCode: ExamCode): CoachExamDailyCache?
}
