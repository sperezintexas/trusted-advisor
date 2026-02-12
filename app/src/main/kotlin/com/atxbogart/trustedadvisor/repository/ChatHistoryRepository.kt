package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.ChatHistory
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatHistoryRepository : MongoRepository<ChatHistory, String> {
    fun findByUserId(userId: String): ChatHistory?
}