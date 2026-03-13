package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.AccessRequest
import com.atxbogart.trustedadvisor.model.AccessRequestStatus
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.repository.AccessRequestRepository
import com.atxbogart.trustedadvisor.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

sealed class AccessRequestResult {
    data class Success(val request: AccessRequest) : AccessRequestResult()
    data class AlreadyExists(val request: AccessRequest) : AccessRequestResult()
    data class AlreadyApproved(val user: User) : AccessRequestResult()
    data object NotFound : AccessRequestResult()
    data class Error(val message: String) : AccessRequestResult()
}

@Service
class AccessRequestService(
    private val accessRequestRepository: AccessRequestRepository,
    private val userRepository: UserRepository,
    private val slackNotificationService: SlackNotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submitRequest(
        email: String,
        displayName: String?,
        reason: String?,
        oauthProvider: String?,
        profileImageUrl: String?
    ): AccessRequestResult {
        val existingUser = userRepository.findByEmail(email)
        if (existingUser != null) {
            log.info("[access-request] User already exists: {}", email)
            return AccessRequestResult.AlreadyApproved(existingUser)
        }

        val existingRequest = accessRequestRepository.findByEmail(email)
        if (existingRequest != null) {
            log.info("[access-request] Request already exists for: {} with status: {}", email, existingRequest.status)
            return AccessRequestResult.AlreadyExists(existingRequest)
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val request = AccessRequest(
            email = email,
            displayName = displayName,
            reason = reason,
            oauthProvider = oauthProvider,
            profileImageUrl = profileImageUrl,
            status = AccessRequestStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        val saved = accessRequestRepository.save(request)
        log.info("[access-request] New request submitted: {} (id={})", email, saved.id)

        slackNotificationService.sendAccessRequestNotification(saved)

        return AccessRequestResult.Success(saved)
    }

    fun getRequestByEmail(email: String): AccessRequest? {
        return accessRequestRepository.findByEmail(email)
    }

    fun getRequestById(id: String): AccessRequest? {
        return accessRequestRepository.findById(id).orElse(null)
    }

    fun getPendingRequests(): List<AccessRequest> {
        return accessRequestRepository.findByStatus(AccessRequestStatus.PENDING)
    }

    fun getAllRequests(): List<AccessRequest> {
        return accessRequestRepository.findAllByOrderByCreatedAtDesc()
    }

    fun approveRequest(requestId: String, reviewerEmail: String, reviewNote: String? = null): AccessRequestResult {
        val request = accessRequestRepository.findById(requestId).orElse(null)
            ?: return AccessRequestResult.NotFound

        if (request.status != AccessRequestStatus.PENDING) {
            log.warn("[access-request] Attempt to approve non-pending request: {} (status={})", requestId, request.status)
            return AccessRequestResult.AlreadyExists(request)
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updatedRequest = request.copy(
            status = AccessRequestStatus.APPROVED,
            reviewedBy = reviewerEmail,
            reviewNote = reviewNote,
            reviewedAt = now,
            updatedAt = now
        )
        val savedRequest = accessRequestRepository.save(updatedRequest)

        val newUser = User(
            email = request.email,
            username = request.email.substringBefore("@"),
            displayName = request.displayName,
            profileImageUrl = request.profileImageUrl,
            registered = false,
            createdAt = now,
            updatedAt = now
        )
        val savedUser = userRepository.save(newUser)
        log.info("[access-request] Approved request {} and created user {} (id={})", requestId, request.email, savedUser.id)

        slackNotificationService.sendAccessApprovedNotification(savedRequest, reviewerEmail)

        return AccessRequestResult.Success(savedRequest)
    }

    fun rejectRequest(requestId: String, reviewerEmail: String, reviewNote: String? = null): AccessRequestResult {
        val request = accessRequestRepository.findById(requestId).orElse(null)
            ?: return AccessRequestResult.NotFound

        if (request.status != AccessRequestStatus.PENDING) {
            log.warn("[access-request] Attempt to reject non-pending request: {} (status={})", requestId, request.status)
            return AccessRequestResult.AlreadyExists(request)
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updatedRequest = request.copy(
            status = AccessRequestStatus.REJECTED,
            reviewedBy = reviewerEmail,
            reviewNote = reviewNote,
            reviewedAt = now,
            updatedAt = now
        )
        val saved = accessRequestRepository.save(updatedRequest)
        log.info("[access-request] Rejected request {} for {}", requestId, request.email)

        slackNotificationService.sendAccessRejectedNotification(saved, reviewerEmail)

        return AccessRequestResult.Success(saved)
    }

    fun getPendingRequestCount(): Int {
        return accessRequestRepository.findByStatus(AccessRequestStatus.PENDING).size
    }
}
