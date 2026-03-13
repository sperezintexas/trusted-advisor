package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.AccessRequestStatus
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.UserRepository
import com.atxbogart.trustedadvisor.service.AccessRequestResult
import com.atxbogart.trustedadvisor.service.AccessRequestService
import com.atxbogart.trustedadvisor.service.StripeCatalogService
import com.atxbogart.trustedadvisor.service.StripeCheckoutService
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
    @Value("\${app.admin-emails:}") private val adminEmailsConfig: String,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String,
    private val userRepository: UserRepository,
    private val accessRequestService: AccessRequestService,
    private val stripeCatalogService: StripeCatalogService,
    private val stripeCheckoutService: StripeCheckoutService
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val adminEmails: Set<String> by lazy {
        adminEmailsConfig.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun isConfiguredAdmin(identity: String): Boolean {
        val normalized = identity.trim().lowercase()
        if (normalized.isEmpty()) return false
        val localPart = normalized.substringBefore("@")
        return adminEmails.any { adminEntry ->
            if (adminEntry.contains("@")) {
                adminEntry == normalized
            } else {
                adminEntry == normalized || adminEntry == localPart
            }
        }
    }

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
        val configuredAdmin = isConfiguredAdmin(email)
        val user = userRepository.findByEmail(email)
        if (user == null) {
            if (configuredAdmin) {
                val now = LocalDateTime.now(ZoneOffset.UTC)
                val adminUser = userRepository.save(
                    User(
                        email = email,
                        username = email.substringBefore("@"),
                        displayName = null,
                        role = UserRole.ADMIN,
                        registered = true,
                        firstLoginAt = now,
                        lastLoginAt = now,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                return ResponseEntity.ok(
                    AuthSessionResponse(
                        allowed = true,
                        needsRegistration = false,
                        accessRequestStatus = null,
                        user = AuthUserView(
                            email = adminUser.email ?: email,
                            username = adminUser.username,
                            displayName = adminUser.displayName,
                            role = UserRole.ADMIN.name
                        )
                    )
                )
            }
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
        val resolvedRole = if (configuredAdmin) UserRole.ADMIN else user.role
        return ResponseEntity.ok(
            AuthSessionResponse(
                allowed = true,
                needsRegistration = needsRegistration,
                accessRequestStatus = null,
                user = AuthUserView(
                    email = user.email ?: email,
                    username = user.username,
                    displayName = user.displayName,
                    role = resolvedRole.name
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
        val configuredAdmin = isConfiguredAdmin(email)

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated: User = existing.copy(
            username = if (body.username.isNotBlank()) body.username else existing.username,
            displayName = body.displayName.ifBlank { existing.displayName },
            role = if (configuredAdmin || existing.role == UserRole.ADMIN) UserRole.ADMIN else requestedRole,
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

    @PostMapping("/auth/subscription/checkout")
    fun createSubscriptionCheckout(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestBody body: CreateCheckoutRequest
    ): ResponseEntity<CreateCheckoutResponse> {
        if (skipAuth) {
            return ResponseEntity.badRequest().body(
                CreateCheckoutResponse(
                    success = false,
                    message = "Checkout disabled in dev skip-auth mode",
                    checkoutUrl = null,
                    sessionId = null
                )
            )
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val tier = body.tier.trim().uppercase()
        if (tier != "PREMIUM") {
            return ResponseEntity.badRequest().body(
                CreateCheckoutResponse(
                    success = false,
                    message = "Checkout is only required for PREMIUM",
                    checkoutUrl = null,
                    sessionId = null
                )
            )
        }
        val result = stripeCheckoutService.createCheckoutSession(
            tier = tier,
            email = email,
            username = body.username.ifBlank { email.substringBefore("@") },
            displayName = body.displayName.ifBlank { body.username.ifBlank { email.substringBefore("@") } }
        ) ?: return ResponseEntity.badRequest().body(
            CreateCheckoutResponse(
                success = false,
                message = "Failed to create Stripe checkout session. Verify STRIPE_SECRET_KEY and STRIPE_PRICE_ID_PREMIUM.",
                checkoutUrl = null,
                sessionId = null
            )
        )
        return ResponseEntity.ok(
            CreateCheckoutResponse(
                success = true,
                message = "Checkout session created",
                checkoutUrl = result.checkoutUrl,
                sessionId = result.sessionId
            )
        )
    }

    @GetMapping("/auth/subscription/checkout/verify")
    fun verifySubscriptionCheckout(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestParam sessionId: String
    ): ResponseEntity<VerifyCheckoutResponse> {
        if (skipAuth) {
            return ResponseEntity.ok(VerifyCheckoutResponse(verified = true, message = "Dev mode"))
        }
        val email = principal?.userId ?: currentEmailFromOAuth2() ?: return ResponseEntity.status(401).build()
        val verification = stripeCheckoutService.verifyCheckoutSession(sessionId, email)
        return ResponseEntity.ok(
            VerifyCheckoutResponse(
                verified = verification.valid,
                message = verification.message
            )
        )
    }

    @GetMapping("/auth/subscription/plans")
    fun subscriptionPlans(): ResponseEntity<SubscriptionPlansResponse> {
        val fallback = SubscriptionPolicy.availablePlans()
        val stripePlans = stripeCatalogService.loadPlansFromStripe(fallback)
        val plans = if (stripePlans != null) {
            stripePlans.map {
                SubscriptionPlanView(
                    tier = it.tier,
                    displayName = it.displayName,
                    monthlyPriceUsd = it.monthlyPriceUsd,
                    features = it.features,
                    stripeProductId = it.stripeProductId,
                    stripePriceId = it.stripePriceId,
                    source = it.source
                )
            }
        } else {
            fallback.map {
                SubscriptionPlanView(
                    tier = it.tier.name,
                    displayName = it.displayName,
                    monthlyPriceUsd = it.monthlyPriceUsd,
                    features = it.features,
                    stripeProductId = null,
                    stripePriceId = null,
                    source = "static"
                )
            }
        }
        return ResponseEntity.ok(
            SubscriptionPlansResponse(
                plans = plans
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
    val features: List<String>,
    val stripeProductId: String? = null,
    val stripePriceId: String? = null,
    val source: String = "static"
)

data class SubscriptionPlansResponse(
    val plans: List<SubscriptionPlanView>
)

data class CreateCheckoutRequest(
    val tier: String,
    val username: String = "",
    val displayName: String = ""
)

data class CreateCheckoutResponse(
    val success: Boolean,
    val message: String,
    val checkoutUrl: String?,
    val sessionId: String?
)

data class VerifyCheckoutResponse(
    val verified: Boolean,
    val message: String
)
