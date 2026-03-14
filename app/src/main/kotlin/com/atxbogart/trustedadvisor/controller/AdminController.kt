package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.AccessRequest
import com.atxbogart.trustedadvisor.model.CoachExamAttempt
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.model.FileIndexStatus
import com.atxbogart.trustedadvisor.model.PersonaFile
import com.atxbogart.trustedadvisor.model.RecommendationStatus
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.CoachExamAttemptRepository
import com.atxbogart.trustedadvisor.repository.PersonaFileRepository
import com.atxbogart.trustedadvisor.repository.UserRepository
import com.atxbogart.trustedadvisor.service.AccessRequestResult
import com.atxbogart.trustedadvisor.service.AccessRequestService
import com.atxbogart.trustedadvisor.service.CoachService
import com.atxbogart.trustedadvisor.service.GeneratedQuestionView
import com.atxbogart.trustedadvisor.service.GenerateQuestionsResult
import com.atxbogart.trustedadvisor.service.PersonaFileResult
import com.atxbogart.trustedadvisor.service.PersonaFileService
import com.atxbogart.trustedadvisor.service.CoachGenerationConfigUpdate
import com.atxbogart.trustedadvisor.service.CoachGenerationConfigView
import com.atxbogart.trustedadvisor.service.CoachQuestionGenerationSchedulerService
import com.atxbogart.trustedadvisor.service.QuestionGeneratorService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/admin")
class AdminController(
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean,
    @Value("\${app.admin-emails:}") private val adminEmailsConfig: String,
    private val accessRequestService: AccessRequestService,
    private val userRepository: UserRepository,
    private val coachExamAttemptRepository: CoachExamAttemptRepository,
    private val personaFileRepository: PersonaFileRepository,
    private val coachService: CoachService,
    private val personaFileService: PersonaFileService,
    private val questionGeneratorService: QuestionGeneratorService,
    private val coachQuestionGenerationSchedulerService: CoachQuestionGenerationSchedulerService
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

    private fun isAdmin(email: String): Boolean {
        if (skipAuth) return true
        if (isConfiguredAdmin(email)) return true
        val user = userRepository.findByEmail(email)
        return user?.role == UserRole.ADMIN
    }

    private fun currentEmailFromOAuth2(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal
        val attrs = principal.attributes
        return (attrs["email"] as? String)
            ?: (attrs["login"] as? String)?.let { "$it@github.local" }
            ?: (attrs["preferred_username"] as? String)?.let { "$it@x.local" }
            ?: (attrs["username"] as? String)?.let { "$it@x.local" }
            ?: (attrs["sub"] as? String)?.let { "x-$it@x.local" }
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

    @PostMapping("/users")
    fun createUser(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @RequestBody body: CreateUserRequestBody
    ): ResponseEntity<AdminUserResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }

        val normalizedEmail = body.email.trim().lowercase()
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            return ResponseEntity.badRequest().body(
                AdminUserResponse(success = false, message = "Valid email is required", user = null)
            )
        }
        if (userRepository.findByEmail(normalizedEmail) != null) {
            return ResponseEntity.badRequest().body(
                AdminUserResponse(success = false, message = "A user with this email already exists", user = null)
            )
        }

        val username = body.username?.trim()?.takeIf { it.isNotBlank() } ?: normalizedEmail.substringBefore("@")
        if (userRepository.findByUsername(username) != null) {
            return ResponseEntity.badRequest().body(
                AdminUserResponse(success = false, message = "A user with this username already exists", user = null)
            )
        }

        val role = body.role?.trim()?.uppercase()?.let {
            runCatching { UserRole.valueOf(it) }.getOrNull()
        } ?: UserRole.BASIC
        val displayName = body.displayName?.trim()?.takeIf { it.isNotBlank() }
        val now = LocalDateTime.now(ZoneOffset.UTC)

        val created = userRepository.save(
            User(
                email = normalizedEmail,
                username = username,
                displayName = displayName,
                role = role,
                registered = body.registered ?: false,
                createdAt = now,
                updatedAt = now
            )
        )

        return ResponseEntity.ok(
            AdminUserResponse(success = true, message = "User added successfully", user = created.toView())
        )
    }

    @PutMapping("/users/{id}")
    fun updateUser(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable id: String,
        @RequestBody body: UpdateUserRequestBody
    ): ResponseEntity<AdminUserResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }

        val existing = userRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val nextEmail = body.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: (existing.email ?: "")
        if (nextEmail.isBlank() || !nextEmail.contains("@")) {
            return ResponseEntity.badRequest().body(
                AdminUserResponse(success = false, message = "Valid email is required", user = null)
            )
        }
        val byEmail = userRepository.findByEmail(nextEmail)
        if (byEmail != null && byEmail.id != existing.id) {
            return ResponseEntity.badRequest().body(
                AdminUserResponse(success = false, message = "A user with this email already exists", user = null)
            )
        }

        val nextUsername = body.username?.trim()?.takeIf { it.isNotBlank() } ?: existing.username
        val byUsername = userRepository.findByUsername(nextUsername)
        if (byUsername != null && byUsername.id != existing.id) {
            return ResponseEntity.badRequest().body(
                AdminUserResponse(success = false, message = "A user with this username already exists", user = null)
            )
        }

        val nextRole = body.role?.trim()?.uppercase()?.let {
            runCatching { UserRole.valueOf(it) }.getOrNull()
        } ?: existing.role
        val nextDisplayName = body.displayName?.trim()?.takeIf { it.isNotBlank() }
        val updated = existing.copy(
            email = nextEmail,
            username = nextUsername,
            displayName = nextDisplayName,
            role = nextRole,
            registered = body.registered ?: existing.registered,
            updatedAt = LocalDateTime.now(ZoneOffset.UTC)
        )
        val saved = userRepository.save(updated)
        return ResponseEntity.ok(
            AdminUserResponse(success = true, message = "User updated successfully", user = saved.toView())
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

    @GetMapping("/personas/{personaId}/documents")
    fun listPersonaDocuments(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable personaId: String
    ): ResponseEntity<AdminDocumentListResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }
        return when (val result = personaFileService.getFilesForPersona(personaId)) {
            is PersonaFileResult.FileList -> ResponseEntity.ok(
                AdminDocumentListResponse(documents = result.files.map { it.toAdminDocumentView() })
            )
            is PersonaFileResult.PersonaNotFound -> ResponseEntity.notFound().build()
            else -> ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/personas/{personaId}/documents")
    fun uploadPersonaDocument(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable personaId: String,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<AdminDocumentResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(
                AdminDocumentResponse(success = false, message = "File is empty", document = null)
            )
        }
        val bytes = file.bytes
        val filename = file.originalFilename ?: "document"
        val contentType = file.contentType
        return when (val result = personaFileService.addFileFromUpload(personaId, filename, bytes, contentType, email)) {
            is PersonaFileResult.Success -> ResponseEntity.ok(
                AdminDocumentResponse(success = true, message = "Document uploaded and indexing queued", document = result.file.toAdminDocumentView())
            )
            is PersonaFileResult.PersonaNotFound -> ResponseEntity.notFound().build()
            is PersonaFileResult.Error -> ResponseEntity.badRequest().body(
                AdminDocumentResponse(success = false, message = result.message, document = null)
            )
            else -> ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/personas/{personaId}/documents/{docId}/index")
    fun reindexPersonaDocument(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable personaId: String,
        @PathVariable docId: String,
        @RequestBody(required = false) body: ReindexRequestBody?
    ): ResponseEntity<AdminDocumentResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }
        val content = body?.content
        val result = if (!content.isNullOrBlank()) {
            personaFileService.queueIndexing(docId, content)
        } else {
            personaFileService.reindexFromStoredChunks(docId)
        }
        return when (result) {
            is PersonaFileResult.Success -> {
                if (result.file.personaId != personaId) {
                    ResponseEntity.badRequest().body(
                        AdminDocumentResponse(success = false, message = "Document does not belong to persona", document = null)
                    )
                } else {
                    ResponseEntity.ok(
                        AdminDocumentResponse(success = true, message = "Document indexing queued", document = result.file.toAdminDocumentView())
                    )
                }
            }
            is PersonaFileResult.NotFound -> ResponseEntity.notFound().build()
            is PersonaFileResult.Error -> ResponseEntity.badRequest().body(
                AdminDocumentResponse(success = false, message = result.message, document = null)
            )
            else -> ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/personas/{personaId}/documents/reindex-all")
    fun reindexAllPersonaDocuments(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable personaId: String
    ): ResponseEntity<AdminBulkReindexResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }
        val result = personaFileService.queueReindexAllForPersona(personaId)
            ?: return ResponseEntity.notFound().build()
        val message = "Queued ${result.queued} files for reindexing (${result.skipped} skipped, ${result.failed} failed)."
        return ResponseEntity.ok(
            AdminBulkReindexResponse(
                success = true,
                message = message,
                personaId = personaId,
                queued = result.queued,
                skipped = result.skipped,
                failed = result.failed
            )
        )
    }

    @DeleteMapping("/personas/{personaId}/documents/{docId}")
    fun deletePersonaDocument(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable personaId: String,
        @PathVariable docId: String
    ): ResponseEntity<AdminDocumentResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }
        return when (val result = personaFileService.removeFile(docId, email)) {
            is PersonaFileResult.Success -> {
                if (result.file.personaId != personaId) {
                    ResponseEntity.badRequest().body(
                        AdminDocumentResponse(success = false, message = "Document does not belong to persona", document = null)
                    )
                } else {
                    ResponseEntity.ok(
                        AdminDocumentResponse(success = true, message = "Document deleted", document = result.file.toAdminDocumentView())
                    )
                }
            }
            is PersonaFileResult.NotFound -> ResponseEntity.notFound().build()
            else -> ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/personas/{personaId}/generate-questions")
    fun generateQuestions(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable personaId: String,
        @RequestBody(required = false) body: GenerateQuestionsRequestBody?
    ): ResponseEntity<GenerateQuestionsResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) {
            return ResponseEntity.status(403).build()
        }
        val count = (body?.count ?: 10).coerceIn(1, 25)
        val examCode = body?.examCode?.let { parseExamCode(it) }
        val saveToPool = body?.saveToPool == true && examCode != null
        return when (val result = questionGeneratorService.generateQuestions(personaId, count, examCode, saveToPool)) {
            is GenerateQuestionsResult.Success -> ResponseEntity.ok(
                GenerateQuestionsResponse(success = true, message = "Generated ${result.questions.size} questions", questions = result.questions)
            )
            is GenerateQuestionsResult.NoContext -> ResponseEntity.badRequest().body(
                GenerateQuestionsResponse(success = false, message = result.message, questions = emptyList())
            )
            is GenerateQuestionsResult.Error -> ResponseEntity.badRequest().body(
                GenerateQuestionsResponse(success = false, message = result.message, questions = emptyList())
            )
        }
    }

    @GetMapping("/coach/generation/configs")
    fun getCoachGenerationConfigs(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<CoachGenerationConfigListResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) return ResponseEntity.status(403).build()
        val configs = coachQuestionGenerationSchedulerService.getConfigs()
        return ResponseEntity.ok(CoachGenerationConfigListResponse(configs = configs))
    }

    @PutMapping("/coach/generation/configs/{examCode}")
    fun updateCoachGenerationConfig(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable examCode: String,
        @RequestBody body: CoachGenerationConfigUpdateRequest
    ): ResponseEntity<CoachGenerationConfigView> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) return ResponseEntity.status(403).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        val updated = coachQuestionGenerationSchedulerService.updateConfig(
            examCode = code,
            update = CoachGenerationConfigUpdate(
                enabled = body.enabled,
                personaId = body.personaId,
                targetPoolSize = body.targetPoolSize,
                intervalMinutes = body.intervalMinutes
            )
        )
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/coach/generation/configs/{examCode}/run")
    fun runCoachGenerationNow(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable examCode: String
    ): ResponseEntity<CoachGenerationConfigView> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) return ResponseEntity.status(403).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        val updated = coachQuestionGenerationSchedulerService.runNow(code)
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/coach/generation/configs/run-all")
    fun runAllCoachGenerationNow(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<CoachGenerationConfigListResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) return ResponseEntity.status(403).build()
        val updated = coachQuestionGenerationSchedulerService.runAllNow()
        return ResponseEntity.ok(CoachGenerationConfigListResponse(configs = updated))
    }

    @GetMapping("/jobs/overview")
    fun getJobsOverview(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<AdminJobsOverviewResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) return ResponseEntity.status(403).build()

        val queued = coachExamAttemptRepository.countByRecommendationStatus(RecommendationStatus.QUEUED)
        val processing = coachExamAttemptRepository.countByRecommendationStatus(RecommendationStatus.PROCESSING)
        val failed = coachExamAttemptRepository.countByRecommendationStatus(RecommendationStatus.FAILED)
        val ready = coachExamAttemptRepository.countByRecommendationStatus(RecommendationStatus.READY)

        val recent = coachExamAttemptRepository.findTop20ByRecommendationStatusInOrderByCompletedAtDesc(
            listOf(RecommendationStatus.QUEUED, RecommendationStatus.PROCESSING, RecommendationStatus.FAILED)
        )
        val personaQueued = personaFileRepository.countByStatus(FileIndexStatus.PENDING)
        val personaIndexing = personaFileRepository.countByStatus(FileIndexStatus.INDEXING)
        val personaFailed = personaFileRepository.countByStatus(FileIndexStatus.FAILED)
        val recentPersonaFailures = personaFileRepository.findTop10ByStatusOrderByUpdatedAtDesc(FileIndexStatus.FAILED)
        val fullExamCache = coachService.getFullExamCacheStatuses().map {
            FullExamCacheView(
                examCode = it.examCode.name,
                expectedQuestionCount = it.expectedQuestionCount,
                hasTodayCache = it.hasTodayCache,
                cacheDate = it.cacheDate,
                questionCount = it.questionCount,
                generatedAt = it.generatedAt
            )
        }

        return ResponseEntity.ok(
            AdminJobsOverviewResponse(
                recommendationQueue = RecommendationQueueOverview(
                    queued = queued,
                    processing = processing,
                    failed = failed,
                    ready = ready,
                    recent = recent.map { it.toRecommendationJobView() }
                ),
                personaIngestion = PersonaIngestionOverview(
                    queued = personaQueued,
                    indexing = personaIndexing,
                    failed = personaFailed,
                    recentFailures = recentPersonaFailures.map { it.toPersonaIngestionFailureView() }
                ),
                fullExamCache = fullExamCache
            )
        )
    }

    @PostMapping("/jobs/recommendations/{attemptId}/retry")
    fun retryRecommendationJob(
        @AuthenticationPrincipal principal: ApiKeyPrincipal?,
        @PathVariable attemptId: String
    ): ResponseEntity<AdminActionResponse> {
        val email = getCurrentEmail(principal) ?: return ResponseEntity.status(401).build()
        if (!isAdmin(email)) return ResponseEntity.status(403).build()

        val attempt = coachExamAttemptRepository.findById(attemptId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val updated = attempt.copy(
            recommendationStatus = RecommendationStatus.QUEUED,
            recommendationError = null
        )
        coachExamAttemptRepository.save(updated)
        return ResponseEntity.ok(
            AdminActionResponse(
                success = true,
                message = "Recommendation job queued for retry",
                request = null
            )
        )
    }

    private fun PersonaFile.toAdminDocumentView() = AdminDocumentView(
        id = id ?: "",
        personaId = personaId,
        name = name,
        status = status.name,
        lastError = lastError,
        chunkCount = chunkCount,
        sizeBytes = sizeBytes,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

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

    private fun parseExamCode(s: String?): ExamCode? = when (s?.uppercase()) {
        "SIE" -> ExamCode.SIE
        "SERIES_7", "SERIES7" -> ExamCode.SERIES_7
        "SERIES_57", "SERIES57" -> ExamCode.SERIES_57
        "SERIES_65", "SERIES65" -> ExamCode.SERIES_65
        else -> null
    }

    private fun User.toView() = UserView(
        id = id ?: "",
        email = email ?: "",
        username = username,
        displayName = displayName,
    role = role.name,
        registered = registered,
        createdAt = createdAt.toString(),
        lastLoginAt = lastLoginAt?.toString()
    )

    private fun CoachExamAttempt.toRecommendationJobView() = RecommendationJobView(
        attemptId = id ?: "",
        userId = userId,
        examCode = examCode.name,
        recommendationStatus = recommendationStatus.name,
        recommendationAttempts = recommendationAttempts,
        percentage = percentage,
        completedAt = completedAt.toString(),
        recommendationUpdatedAt = recommendationUpdatedAt?.toString(),
        recommendationError = recommendationError
    )

    private fun PersonaFile.toPersonaIngestionFailureView() = PersonaIngestionFailureView(
        fileId = id ?: "",
        personaId = personaId,
        fileName = name,
        sourceType = sourceType.name,
        updatedAt = updatedAt.toString(),
        lastError = lastError ?: "Unknown failure"
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
    val role: String,
    val registered: Boolean,
    val createdAt: String,
    val lastLoginAt: String?
)

data class UserListResponse(
    val users: List<UserView>,
    val total: Int
)

data class CreateUserRequestBody(
    val email: String,
    val username: String? = null,
    val displayName: String? = null,
    val role: String? = null,
    val registered: Boolean? = null
)

data class UpdateUserRequestBody(
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val role: String? = null,
    val registered: Boolean? = null
)

data class AdminUserResponse(
    val success: Boolean,
    val message: String,
    val user: UserView?
)

data class AdminDocumentView(
    val id: String,
    val personaId: String,
    val name: String,
    val status: String,
    val lastError: String?,
    val chunkCount: Int,
    val sizeBytes: Long?,
    val createdAt: String,
    val updatedAt: String
)

data class AdminDocumentListResponse(
    val documents: List<AdminDocumentView>
)

data class AdminDocumentResponse(
    val success: Boolean,
    val message: String,
    val document: AdminDocumentView?
)

data class ReindexRequestBody(
    val content: String? = null
)

data class AdminBulkReindexResponse(
    val success: Boolean,
    val message: String,
    val personaId: String,
    val queued: Int,
    val skipped: Int,
    val failed: Int
)

data class GenerateQuestionsRequestBody(
    val count: Int? = 10,
    val examCode: String? = null,
    val saveToPool: Boolean? = null
)

data class GenerateQuestionsResponse(
    val success: Boolean,
    val message: String,
    val questions: List<GeneratedQuestionView>
)

data class CoachGenerationConfigListResponse(
    val configs: List<CoachGenerationConfigView>
)

data class CoachGenerationConfigUpdateRequest(
    val enabled: Boolean? = null,
    val personaId: String? = null,
    val targetPoolSize: Int? = null,
    val intervalMinutes: Int? = null
)

data class AdminJobsOverviewResponse(
    val recommendationQueue: RecommendationQueueOverview,
    val personaIngestion: PersonaIngestionOverview,
    val fullExamCache: List<FullExamCacheView>
)

data class PersonaIngestionOverview(
    val queued: Long,
    val indexing: Long,
    val failed: Long,
    val recentFailures: List<PersonaIngestionFailureView>
)

data class PersonaIngestionFailureView(
    val fileId: String,
    val personaId: String,
    val fileName: String,
    val sourceType: String,
    val updatedAt: String,
    val lastError: String
)

data class RecommendationQueueOverview(
    val queued: Long,
    val processing: Long,
    val failed: Long,
    val ready: Long,
    val recent: List<RecommendationJobView>
)

data class RecommendationJobView(
    val attemptId: String,
    val userId: String,
    val examCode: String,
    val recommendationStatus: String,
    val recommendationAttempts: Int,
    val percentage: Double,
    val completedAt: String,
    val recommendationUpdatedAt: String?,
    val recommendationError: String?
)

data class FullExamCacheView(
    val examCode: String,
    val expectedQuestionCount: Int,
    val hasTodayCache: Boolean,
    val cacheDate: String?,
    val questionCount: Int?,
    val generatedAt: String?
)
