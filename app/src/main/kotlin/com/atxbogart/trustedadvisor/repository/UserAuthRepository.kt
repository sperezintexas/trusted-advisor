package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.UserAuth
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAuthRepository : MongoRepository<UserAuth, String> {
    fun findByProviderAndProviderUserId(provider: String, providerUserId: String): UserAuth?
    fun findByUserIdAndProvider(userId: String, provider: String): UserAuth?
}
