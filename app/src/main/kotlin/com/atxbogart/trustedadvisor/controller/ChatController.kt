package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.ChatRequest
import com.atxbogart.trustedadvisor.model.ChatResponse
import com.atxbogart.trustedadvisor.service.ChatService
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = ["http://localhost:3000"])
class ChatController(private val chatService: ChatService) {

    @PostMapping
    fun chat(@RequestBody request: ChatRequest): ChatResponse =
        chatService.sendMessage(request)

    @GetMapping("/history")
    fun history(@RequestParam userId: String = "default"): ResponseEntity<List<ChatMessage>> =
        try {
            ResponseEntity.ok(chatService.getHistory(userId))
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }
}