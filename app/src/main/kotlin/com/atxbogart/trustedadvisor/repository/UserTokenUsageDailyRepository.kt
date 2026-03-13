package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.UserTokenUsageDaily
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTokenUsageDailyRepository : MongoRepository<UserTokenUsageDaily, String> {
    fun findByUserIdAndDate(userId: String, date: String): UserTokenUsageDaily?
}
