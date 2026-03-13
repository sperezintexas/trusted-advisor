package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.AccessRequest
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.UserRepository
import com.atxbogart.trustedadvisor.service.AccessRequestResult
import com.atxbogart.trustedadvisor.service.AccessRequestService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean,
    @Value("\${app.admin-emails:}") private val adminEmailsConfig: String,
    private val accessRequestService: AccessRequestService,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val adminEmails: Set<String> by lazy {
        adminEmailsConfig.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun isAdmin(email: String): Boolean {
        if (skipAuth) return true
        if (adminEmails.contains(email.lowercase())) return true
        val user = userRepository.findByEmail(email)
        return user?.role == UserRole.ADMIN
    }

    private fun currentEmailFromOAuth2(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal as? OAuth2User ?: return null
        val attrs = principal.attributes
        return (attrs["email"] as? String)
            ?: (attrs["login"] as? String)?.let { "$it@github.local" }
    }

    private fun getCurrentEmail(principal: ApiKeyPrincipal?): String? {
        return principal?.userId ?: currentEmailFromOAuth2() ?: if (skipAuth) "admin@dev.local" else null
    }

    @GetMapping("/access-requests")
    fun listAccessRequests(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<AccessRequestListResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            log.warn("[admin] Non-admin user {} attempted to list access requests", email)
            return ResponseEntity.status(403).build()
        }

        val requests = if (status != null) {
            try {
                val statusEnum = com.atxbogart.trustedadvisor.model.AccessRequestStatus.valueOf(status.uppercase())
                accessRequestService.getPendingRequests().filter { it.status == statusEnum }
            } catch (e: IllegalArgumentException) {
                accessRequestService.getAllRequests()
            }
        } else {
            accessRequestService.getAllRequests()
        }

        return ResponseEntity.ok(
            AccessRequestListResponse(
                requests = requests.map { it.toView() },
                total = requests.size
            )
        )
    }

    @GetMapping("/access-requests/{id}")
    fun getAccessRequest(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable id: String
    ): ResponseEntity<AccessRequestView> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }

        val request = accessRequestService.getRequestById(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(request.toView())
    }

    @PostMapping("/access-requests/{id}/approve")
    fun approveAccessRequest(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable id: String,
        @RequestBody(required = false) body: ReviewRequestBody?
    ): ResponseEntity<AdminActionResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            log.warn("[admin] Non-admin user {} attempted to approve access request {}", email, id)
            return ResponseEntity.status(403).build()
        }

        return when (val result = accessRequestService.approveRequest(id, email, body?.note)) {
            is AccessRequestResult.Success -> {
                log.info("[admin] Access request {} approved by {}", id, email)
                ResponseEntity.ok(
                    AdminActionResponse(
                        success = true,
                        message = "Access request approved. User has been added.",
                        request = result.request.toView()
                    )
                )
            }
            is AccessRequestResult.NotFound -> ResponseEntity.notFound().build()
            is AccessRequestResult.AlreadyExists -> ResponseEntity.badRequest().body(
                AdminActionResponse(
                    success = false,
                    message = "Request has already been processed (status: ${result.request.status})",
                    request = result.request.toView()
                )
            )
            else -> ResponseEntity.badRequest().body(
                AdminActionResponse(
                    success = false,
                    message = "Failed to approve request",
                    request = null
                )
            )
        }
    }

    @PostMapping("/access-requests/{id}/reject")
    fun rejectAccessRequest(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable id: String,
        @RequestBody(required = false) body: ReviewRequestBody?
    ): ResponseEntity<AdminActionResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            log.warn("[admin] Non-admin user {} attempted to reject access request {}", email, id)
            return ResponseEntity.status(403).build()
        }

        return when (val result = accessRequestService.rejectRequest(id, email, body?.note)) {
            is AccessRequestResult.Success -> {
                log.info("[admin] Access request {} rejected by {}", id, email)
                ResponseEntity.ok(
                    AdminActionResponse(
                        success = true,
                        message = "Access request rejected.",
                        request = result.request.toView()
                    )
                )
            }
            is AccessRequestResult.NotFound -> ResponseEntity.notFound().build()
            is AccessRequestResult.AlreadyExists -> ResponseEntity.badRequest().body(
                AdminActionResponse(
                    success = false,
                    message = "Request has already been processed (status: ${result.request.status})",
                    request = result.request.toView()
                )
            )
            else -> ResponseEntity.badRequest().body(
                AdminActionResponse(
                    success = false,
                    message = "Failed to reject request",
                    request = null
                )
            )
        }
    }

    @GetMapping("/users")
    fun listUsers(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<UserListResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }

        val users = userRepository.findAll()
        return ResponseEntity.ok(
            UserListResponse(
                users = users.map { it.toView() },
                total = users.size
            )
        )
    }

    @DeleteMapping("/users/{id}")
    fun deleteUser(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable id: String
    ): ResponseEntity<AdminActionResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }

        val user = userRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        userRepository.deleteById(id)
        log.info("[admin] User {} deleted by {}", user.email, email)

        return ResponseEntity.ok(
            AdminActionResponse(
                success = true,
                message = "User deleted successfully",
                request = null
            )
        )
    }

    private fun AccessRequest.toView() = AccessRequestView(
        id = id ?: "",
        email = email,
        displayName = displayName,
        reason = reason,
        status = status.name,
        oauthProvider = oauthProvider,
        profileImageUrl = profileImageUrl,
        reviewedBy = reviewedBy,
        reviewNote = reviewNote,
        createdAt = createdAt.toString(),
        reviewedAt = reviewedAt?.toString()
    )

    private fun User.toView() = UserView(
        id = id ?: "",
        email = email ?: "",
        username = username,
        displayName = displayName,
        registered = registered,
        createdAt = createdAt.toString(),
        lastLoginAt = lastLoginAt?.toString()
    )
}

data class AccessRequestView(
    val id: String,
    val email: String,
    val displayName: String?,
    val reason: String?,
    val status: String,
    val oauthProvider: String?,
    val profileImageUrl: String?,
    val reviewedBy: String?,
    val reviewNote: String?,
    val createdAt: String,
    val reviewedAt: String?
)

data class AccessRequestListResponse(
    val requests: List<AccessRequestView>,
    val total: Int
)

data class ReviewRequestBody(
    val note: String? = null
)

data class AdminActionResponse(
    val success: Boolean,
    val message: String,
    val request: AccessRequestView?
)

data class UserView(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String?,
    val registered: Boolean,
    val createdAt: String,
    val lastLoginAt: String?
)

data class UserListResponse(
    val users: List<UserView>,
    val total: Int
)
