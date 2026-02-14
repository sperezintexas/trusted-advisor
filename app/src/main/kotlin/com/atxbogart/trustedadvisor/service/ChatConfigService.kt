package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.ChatConfig
import com.atxbogart.trustedadvisor.model.ChatConfigUpdate
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class ChatConfigService {

    private val configHolder = AtomicReference(ChatConfig())

    fun getConfig(): ChatConfig = configHolder.get()

    fun updateConfig(partial: ChatConfigUpdate) {
        val current = configHolder.get()
        val merged = ChatConfig(
            debug = partial.debug ?: current.debug,
            tools = partial.tools ?: current.tools,
            context = partial.context ?: current.context
        )
        configHolder.set(merged)
    }
}
