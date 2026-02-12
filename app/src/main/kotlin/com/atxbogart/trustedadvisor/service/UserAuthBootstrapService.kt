package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.UserAuth
import com.atxbogart.trustedadvisor.repository.UserAuthRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Service

@Service
class UserAuthBootstrapService(
    private val repository: UserAuthRepository,
    private val mongoTemplate: MongoTemplate,
    @Value("\${auth.seed.enabled:true}") private val seedEnabled: Boolean,
    @Value("\${auth.seed.user-id:default}") private val configuredSeedUserId: String,
    @Value("\${auth.seed.provider:x}") private val configuredSeedProvider: String,
    @Value("\${auth.seed.provider-user-id:x-seed-user-001}") private val configuredSeedProviderUserId: String,
    @Value("\${auth.seed.provider-username:trusted_advisor_x_seed}") private val configuredSeedProviderUsername: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    private fun bootstrap() {
        if (!seedEnabled) {
            logger.info("user_auth seed is disabled")
            return
        }

        try {
            ensureIndexes()
            seedDefaultUserAuth()
        } catch (e: Exception) {
            logger.warn("Could not initialize user_auth (Mongo may be down): {}", e.message)
        }
    }

    private fun ensureIndexes() {
        val indexOps = mongoTemplate.indexOps(UserAuth::class.java)
        indexOps.ensureIndex(
            Index()
                .on("provider", Sort.Direction.ASC)
                .on("providerUserId", Sort.Direction.ASC)
                .unique()
                .named("provider_providerUserId_unique")
        )
        indexOps.ensureIndex(
            Index()
                .on("userId", Sort.Direction.ASC)
                .on("provider", Sort.Direction.ASC)
                .unique()
                .named("userId_provider_unique")
        )
    }

    private fun seedDefaultUserAuth() {
        val seedUserId = configuredSeedUserId.normalizedOr("default")
        val seedProvider = configuredSeedProvider.normalizedOr("x").lowercase()
        val seedProviderUserId = configuredSeedProviderUserId.normalizedOr("x-seed-user-001")
        val seedProviderUsername = configuredSeedProviderUsername.normalizedOr("trusted_advisor_x_seed")

        if (repository.findByProviderAndProviderUserId(seedProvider, seedProviderUserId) != null) {
            logger.debug(
                "user_auth seed already exists for provider={} providerUserId={}",
                seedProvider,
                seedProviderUserId
            )
            return
        }

        repository.save(
            UserAuth(
                userId = seedUserId,
                provider = seedProvider,
                providerUserId = seedProviderUserId,
                providerUsername = seedProviderUsername
            )
        )
        logger.info(
            "Seeded user_auth entry for userId={} provider={} providerUserId={}",
            seedUserId,
            seedProvider,
            seedProviderUserId
        )
    }

    private fun String.normalizedOr(defaultValue: String): String =
        trim().ifEmpty { defaultValue }
}
