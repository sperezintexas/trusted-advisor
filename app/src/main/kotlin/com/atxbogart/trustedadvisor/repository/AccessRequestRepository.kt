package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.AccessRequest
import com.atxbogart.trustedadvisor.model.AccessRequestStatus
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AccessRequestRepository : MongoRepository<AccessRequest, String> {
    fun findByEmail(email: String): AccessRequest?
    fun findByEmailAndStatus(email: String, status: AccessRequestStatus): AccessRequest?
    fun findByStatus(status: AccessRequestStatus): List<AccessRequest>
    fun findAllByOrderByCreatedAtDesc(): List<AccessRequest>
    fun existsByEmailAndStatus(email: String, status: AccessRequestStatus): Boolean
}
