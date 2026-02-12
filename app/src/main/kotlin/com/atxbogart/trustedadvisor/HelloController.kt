package com.atxbogart.trustedadvisor

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/")
    fun home(): String {
        return "Welcome to Trusted Advisor! Family AI Experts powered by Grok."
    }

    @GetMapping("/health")
    fun health(): String = "OK"

    // /personas moved to PersonaController
}