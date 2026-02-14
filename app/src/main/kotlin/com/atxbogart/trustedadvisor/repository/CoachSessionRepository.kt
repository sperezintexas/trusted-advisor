package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.CoachSession
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CoachSessionRepository : MongoRepository<CoachSession, String> {
}