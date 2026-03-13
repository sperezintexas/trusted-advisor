package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.AccessRequestStatus
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.UserRepository
import com.atxbogart.trustedadvisor.service.AccessRequestResult
import com.atxbogart.trustedadvisor.service.AccessRequestService
import com.atxbogart.trustedadvisor.service.SubscriptionPolicy
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/api")
class AuthController(
    @Value("\${app.auth-debug:false}") private val authDebug: Boolean,
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean,
    private val userRepository: UserRepository,
    private val accessRequestService: AccessRequestService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<MeResponse?> {
        if (authDebug) log.info("[auth] GET /api/me: principal present={}", principal != null)
        val effectiveUserId = principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else return ResponseEntity.status(401).build()
        return ResponseEntity.ok(
            MeResponse(
                id = effectiveUserId,
                username = "api",
                displayName = null,
                profileImageUrl = null
            )
        )
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        if (authDebug) log.info("[auth] POST /api/logout")
        return ResponseEntity.noContent().build()
    }

    /**
     * Current auth session derived from principal + users collection.
     * - When skip-auth is enabled, always returns allowed + needsRegistration=false for a synthetic dev user.
     * - When auth is enforced, looks up the user by principal userId (treated as email) in Mongo.
     * - If user is not found, checks for pending access request.
     */
    @GetMapping("/auth/session")
    fun authSession(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<AuthSessionResponse> {
        if (skipAuth) {
            return ResponseEntity.ok(
                AuthSessionResponse(
                    allowed = true,
                    needsRegistration = false,
                    accessRequestStatus = null,
                    user = AuthUserView(
                        email = "dev-user@example.com",
                        username = "dev-user",
                        displayName = "Developer",
                        role = "ADMIN"
                    )
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val user = userRepository.findByEmail(email)
        if (user == null) {
            val accessRequest = accessRequestService.getRequestByEmail(email)
            return ResponseEntity.ok(
                AuthSessionResponse(
                    allowed = false,
                    needsRegistration = false,
                    accessRequestStatus = accessRequest?.status?.name,
                    user = AuthUserView(
                        email = email,
                        username = email.substringBefore("@"),
                        displayName = null,
                        role = "BASIC"
                    )
                )
            )
        }
        val needsRegistration = !user.registered
        return ResponseEntity.ok(
            AuthSessionResponse(
                allowed = true,
                needsRegistration = needsRegistration,
                accessRequestStatus = null,
                user = AuthUserView(
                    email = user.email ?: email,
                    username = user.username,
                    displayName = user.displayName,
                    role = user.role.name
                )
            )
        )
    }

    @PostMapping("/auth/request-access")
    fun requestAccess(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestBody body: AccessRequestBody
    ): ResponseEntity<AccessRequestResponse> {
        if (skipAuth) {
            return ResponseEntity.ok(
                AccessRequestResponse(
                    success = true,
                    message = "Dev mode: access request simulated",
                    status = "PENDING"
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val oauthInfo = getOAuth2Info()

        return when (val result = accessRequestService.submitRequest(
            email = email,
            displayName = body.displayName ?: oauthInfo?.displayName,
            reason = body.reason,
            oauthProvider = oauthInfo?.provider,
            profileImageUrl = oauthInfo?.profileImageUrl
        )) {
            is AccessRequestResult.Success -> ResponseEntity.ok(
                AccessRequestResponse(
                    success = true,
                    message = "Access request submitted successfully",
                    status = result.request.status.name
                )
            )
            is AccessRequestResult.AlreadyExists -> ResponseEntity.ok(
                AccessRequestResponse(
                    success = false,
                    message = "Access request already exists",
                    status = result.request.status.name
                )
            )
            is AccessRequestResult.AlreadyApproved -> ResponseEntity.ok(
                AccessRequestResponse(
                    success = false,
                    message = "User already has access",
                    status = "APPROVED"
                )
            )
            is AccessRequestResult.Error -> ResponseEntity.badRequest().body(
                AccessRequestResponse(
                    success = false,
                    message = result.message,
                    status = null
                )
            )
            is AccessRequestResult.NotFound -> ResponseEntity.badRequest().body(
                AccessRequestResponse(
                    success = false,
                    message = "Request not found",
                    status = null
                )
            )
        }
    }

    @GetMapping("/auth/access-request/status")
    fun accessRequestStatus(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<AccessRequestStatusResponse> {
        if (skipAuth) {
            return ResponseEntity.ok(
                AccessRequestStatusResponse(
                    hasRequest = false,
                    status = null,
                    createdAt = null
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val request = accessRequestService.getRequestByEmail(email)
        return ResponseEntity.ok(
            AccessRequestStatusResponse(
                hasRequest = request != null,
                status = request?.status?.name,
                createdAt = request?.createdAt?.toString()
            )
        )
    }

    private fun getOAuth2Info(): OAuth2Info? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal as? OAuth2User ?: return null
        val attrs = principal.attributes
        val provider = auth.authorizedClientRegistrationId
        val displayName = (attrs["name"] as? String) ?: (attrs["login"] as? String)
        val profileImageUrl = (attrs["picture"] as? String) ?: (attrs["avatar_url"] as? String)
        return OAuth2Info(provider, displayName, profileImageUrl)
    }

    @PostMapping("/auth/register")
    fun register(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestBody body: RegistrationRequest
    ): ResponseEntity<AuthSessionResponse> {
        if (skipAuth) {
            return ResponseEntity.ok(
                AuthSessionResponse(
                    allowed = true,
                    needsRegistration = false,
                    accessRequestStatus = null,
                    user = AuthUserView(
                        email = "dev-user@example.com",
                        username = body.username.ifBlank { "dev-user" },
                        displayName = body.displayName.ifBlank { "Developer" },
                        role = "ADMIN"
                    )
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val existing = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(403).build()

        val requestedRole = SubscriptionPolicy.resolveUserRole(existing.role, body.tier)

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated: User = existing.copy(
            username = if (body.username.isNotBlank()) body.username else existing.username,
            displayName = body.displayName.ifBlank { existing.displayName },
            role = if (existing.role == UserRole.ADMIN) UserRole.ADMIN else requestedRole,
            registered = true,
            firstLoginAt = existing.firstLoginAt ?: now,
            lastLoginAt = now,
            updatedAt = now
        )
        val saved = userRepository.save(updated)
        return ResponseEntity.ok(
            AuthSessionResponse(
                allowed = true,
                needsRegistration = false,
                accessRequestStatus = null,
                user = AuthUserView(
                    email = saved.email ?: email,
                    username = saved.username,
                    displayName = saved.displayName,
                    role = saved.role.name
                )
            )
        )
    }

    @GetMapping("/auth/subscription/plans")
    fun subscriptionPlans(): ResponseEntity<SubscriptionPlansResponse> {
        return ResponseEntity.ok(
            SubscriptionPlansResponse(
                plans = SubscriptionPolicy.availablePlans().map {
                    SubscriptionPlanView(
                        tier = it.tier.name,
                        displayName = it.displayName,
                        monthlyPriceUsd = it.monthlyPriceUsd,
                        features = it.features
                    )
                }
            )
        )
    }

    private fun currentEmailFromOAuth2(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal as? OAuth2User ?: return null
        val attrs = principal.attributes
        return (attrs["email"] as? String)
            ?: (attrs["login"] as? String)?.let { "$it@github.local" }
    }
}

data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val profileImageUrl: String?
)

data class AuthUserView(
    val email: String,
    val username: String,
    val displayName: String?,
    val role: String
)

data class AuthSessionResponse(
    val allowed: Boolean,
    val needsRegistration: Boolean,
    val accessRequestStatus: String?,
    val user: AuthUserView?
)

data class RegistrationRequest(
    val username: String = "",
    val displayName: String = "",
    val tier: String = "BASIC"
)

data class AccessRequestBody(
    val displayName: String? = null,
    val reason: String? = null
)

data class AccessRequestResponse(
    val success: Boolean,
    val message: String,
    val status: String?
)

data class AccessRequestStatusResponse(
    val hasRequest: Boolean,
    val status: String?,
    val createdAt: String?
)

data class OAuth2Info(
    val provider: String?,
    val displayName: String?,
    val profileImageUrl: String?
)

data class SubscriptionPlanView(
    val tier: String,
    val displayName: String,
    val monthlyPriceUsd: String,
    val features: List<String>
)

data class SubscriptionPlansResponse(
    val plans: List<SubscriptionPlanView>
)
