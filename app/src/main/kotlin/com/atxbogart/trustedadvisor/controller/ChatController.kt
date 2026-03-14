package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.ChatConfig
import com.atxbogart.trustedadvisor.model.ChatConfigUpdate
import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.ChatRequest
import com.atxbogart.trustedadvisor.model.ChatResponse
import com.atxbogart.trustedadvisor.service.ChatConfigService
import com.atxbogart.trustedadvisor.service.ChatService
import com.atxbogart.trustedadvisor.service.ChatLimitsView
import com.atxbogart.trustedadvisor.service.GrokTestResult
import com.atxbogart.trustedadvisor.service.GrokService
import com.atxbogart.trustedadvisor.service.UsageLimitService
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = ["http://localhost:3000"])
class ChatController(
    private val chatService: ChatService,
    private val chatConfigService: ChatConfigService,
    private val grokService: GrokService,
    private val usageLimitService: UsageLimitService,
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean
) {

    @PostMapping
    fun chat(
        @RequestBody request: ChatRequest,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<Any> {
        val userId = principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else return ResponseEntity.status(401).build()
        if (!skipAuth) {
            val chatUsage = usageLimitService.chatUsageStatus(userId)
            if (chatUsage.isAtLimit) {
                val limit = chatUsage.limit ?: UsageLimitService.BASIC_CHAT_QUESTION_LIMIT
                return ResponseEntity.status(403).body(
                    ErrorResponse("Chat limit reached for BASIC plan ($limit questions). Upgrade to PREMIUM for unlimited chat.")
                )
            }
        }
        val scopedRequest = request.copy(userId = userId)
        return ResponseEntity.ok(chatService.sendMessage(scopedRequest))
    }

    @GetMapping("/history")
    fun history(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<List<ChatMessage>> {
        val userId = principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else return ResponseEntity.status(401).build()
        return try {
            ResponseEntity.ok(chatService.getHistory(userId))
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }
    }

    @GetMapping("/limits")
    fun limits(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<ChatLimitsView> {
        val userId = principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else return ResponseEntity.status(401).build()
        return ResponseEntity.ok(chatService.getChatLimits(userId))
    }

    @GetMapping("/config")
    fun getConfig(): ChatConfig = chatConfigService.getConfig()

    @PutMapping("/config")
    fun putConfig(@RequestBody update: ChatConfigUpdate): ChatConfig {
        chatConfigService.updateConfig(update)
        return chatConfigService.getConfig()
    }

    @GetMapping("/config/test")
    fun testGrokConnection(): GrokTestResult = grokService.testConnection()

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
}