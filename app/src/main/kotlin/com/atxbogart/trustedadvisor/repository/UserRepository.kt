package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.User
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<User, String> {
    fun findByXId(xId: String): User?
    fun findByUsername(username: String): User?
}
