package com.atxbogart.trustedadvisor.config

import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.dao.DataAccessException

@Configuration
class UserSeed(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Only seed: placeholder user for X OAuth2; link by logging in with X. */
    @EventListener(ApplicationReadyEvent::class)
    fun seedAtxbogart() {
        try {
            if (userRepository.findByUsername("atxbogart") != null) return
            userRepository.save(
                User(
                    username = "atxbogart",
                    displayName = "atxbogart",
                    xId = null
                )
            )
            log.info("Seeded user: atxbogart (link by logging in with X)")
        } catch (e: DataAccessException) {
            log.warn("Could not seed atxbogart user (is MongoDB running?): {}", e.message)
        }
    }
}
