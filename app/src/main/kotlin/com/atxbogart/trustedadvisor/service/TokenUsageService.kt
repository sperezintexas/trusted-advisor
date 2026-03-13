package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.repository.UserTokenUsageDailyRepository
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class TokenUsageService(
    private val usageRepository: UserTokenUsageDailyRepository,
    private val mongoTemplate: MongoTemplate
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun getTodayUsage(userId: String): Long {
        val dateKey = todayDateKey()
        return usageRepository.findByUserIdAndDate(userId, dateKey)?.tokens ?: 0L
    }

    fun addTodayUsage(userId: String, tokensToAdd: Long): Long {
        if (tokensToAdd <= 0L) return getTodayUsage(userId)
        val dateKey = todayDateKey()
        val query = Query(
            Criteria.where("userId").`is`(userId)
                .and("date").`is`(dateKey)
        )
        val update = Update()
            .setOnInsert("userId", userId)
            .setOnInsert("date", dateKey)
            .inc("tokens", tokensToAdd)
            .set("updatedAt", LocalDateTime.now(ZoneOffset.UTC))
        val updated = mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            com.atxbogart.trustedadvisor.model.UserTokenUsageDaily::class.java
        )
        return updated?.tokens ?: getTodayUsage(userId)
    }

    private fun todayDateKey(): String = LocalDate.now(ZoneOffset.UTC).format(dateFormatter)
}
