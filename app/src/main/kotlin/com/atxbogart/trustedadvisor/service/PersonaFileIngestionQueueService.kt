package com.atxbogart.trustedadvisor.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PersonaFileIngestionQueueService(
    private val personaFileService: PersonaFileService
) {
    @Scheduled(fixedDelay = 5000L)
    fun processPendingIngestionJobs() {
        repeat(5) {
            personaFileService.processNextPendingFile()
        }
    }
}
