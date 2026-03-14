package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.FileIndexStatus
import com.atxbogart.trustedadvisor.model.PersonaFile
import com.atxbogart.trustedadvisor.model.PersonaFileChunk
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface PersonaFileRepository : MongoRepository<PersonaFile, String> {
    fun findByPersonaId(personaId: String): List<PersonaFile>
    fun findByPersonaIdAndStatus(personaId: String, status: FileIndexStatus): List<PersonaFile>
    fun findByPersonaIdAndSourceFileId(personaId: String, sourceFileId: String): PersonaFile?
    fun findTop20ByStatusOrderByUpdatedAtAsc(status: FileIndexStatus): List<PersonaFile>
    fun findTop10ByStatusOrderByUpdatedAtDesc(status: FileIndexStatus): List<PersonaFile>
    fun deleteByPersonaId(personaId: String)
    fun countByPersonaId(personaId: String): Long
    fun countByStatus(status: FileIndexStatus): Long
}

@Repository
interface PersonaFileChunkRepository : MongoRepository<PersonaFileChunk, String> {
    fun findByFileIdOrderByChunkIndexAsc(fileId: String): List<PersonaFileChunk>
    fun findByPersonaId(personaId: String): List<PersonaFileChunk>
    fun deleteByFileId(fileId: String)
    fun deleteByPersonaId(personaId: String)
    fun countByFileId(fileId: String): Long
}
