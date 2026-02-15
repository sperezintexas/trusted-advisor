package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.model.ChatConfig
import com.atxbogart.trustedadvisor.model.ChatConfigUpdate
import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.ChatRequest
import com.atxbogart.trustedadvisor.model.ChatResponse
import com.atxbogart.trustedadvisor.service.ChatConfigService
import com.atxbogart.trustedadvisor.service.ChatService
import com.atxbogart.trustedadvisor.service.GrokTestResult
import com.atxbogart.trustedadvisor.service.GrokService
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = ["http://localhost:3000"])
class ChatController(
    private val chatService: ChatService,
    private val chatConfigService: ChatConfigService,
    private val grokService: GrokService
) {

    @PostMapping
    fun chat(
        @RequestBody request: ChatRequest,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<ChatResponse> {
        val userId = principal?.userId ?: return ResponseEntity.status(401).build()
        val scopedRequest = request.copy(userId = userId)
        return ResponseEntity.ok(chatService.sendMessage(scopedRequest))
    }

    @GetMapping("/history")
    fun history(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<List<ChatMessage>> {
        val userId = principal?.userId ?: return ResponseEntity.status(401).build()
        return try {
            ResponseEntity.ok(chatService.getHistory(userId))
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }
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
}