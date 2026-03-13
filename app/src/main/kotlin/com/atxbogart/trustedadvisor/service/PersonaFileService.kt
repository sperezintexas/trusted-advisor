package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import com.atxbogart.trustedadvisor.repository.PersonaFileChunkRepository
import com.atxbogart.trustedadvisor.repository.PersonaFileRepository
import com.atxbogart.trustedadvisor.repository.PersonaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

sealed class PersonaFileResult {
    data class Success(val file: PersonaFile) : PersonaFileResult()
    data class FileList(val files: List<PersonaFile>) : PersonaFileResult()
    data object NotFound : PersonaFileResult()
    data object PersonaNotFound : PersonaFileResult()
    data object AlreadyExists : PersonaFileResult()
    data class Error(val message: String) : PersonaFileResult()
}

data class AddFileRequest(
    val sourceType: String = "GOOGLE_DRIVE",
    val sourceFileId: String,
    val sourceUrl: String? = null,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
)

@Service
class PersonaFileService(
    private val personaFileRepository: PersonaFileRepository,
    private val personaFileChunkRepository: PersonaFileChunkRepository,
    private val personaRepository: PersonaRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_CHUNK_SIZE = 4000
        const val MAX_FILES_PER_PERSONA = 20
    }

    fun getFilesForPersona(personaId: String): PersonaFileResult {
        if (!personaRepository.existsById(personaId)) {
            return PersonaFileResult.PersonaNotFound
        }
        val files = personaFileRepository.findByPersonaId(personaId)
        return PersonaFileResult.FileList(files)
    }

    fun getFile(fileId: String): PersonaFileResult {
        val file = personaFileRepository.findById(fileId).orElse(null)
            ?: return PersonaFileResult.NotFound
        return PersonaFileResult.Success(file)
    }

    fun addFile(personaId: String, request: AddFileRequest, userId: String): PersonaFileResult {
        if (!personaRepository.existsById(personaId)) {
            return PersonaFileResult.PersonaNotFound
        }

        val existingCount = personaFileRepository.countByPersonaId(personaId)
        if (existingCount >= MAX_FILES_PER_PERSONA) {
            return PersonaFileResult.Error("Maximum files per persona ($MAX_FILES_PER_PERSONA) reached")
        }

        val existing = personaFileRepository.findByPersonaIdAndSourceFileId(personaId, request.sourceFileId)
        if (existing != null) {
            return PersonaFileResult.AlreadyExists
        }

        val sourceType = try {
            FileSourceType.valueOf(request.sourceType.uppercase())
        } catch (e: IllegalArgumentException) {
            FileSourceType.GOOGLE_DRIVE
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val file = PersonaFile(
            personaId = personaId,
            sourceType = sourceType,
            sourceFileId = request.sourceFileId,
            sourceUrl = request.sourceUrl,
            name = request.name,
            mimeType = request.mimeType,
            sizeBytes = request.sizeBytes,
            status = FileIndexStatus.PENDING,
            createdBy = userId,
            createdAt = now,
            updatedAt = now
        )

        val saved = personaFileRepository.save(file)
        log.info("[persona-file] Added file {} to persona {} by {}", saved.id, personaId, userId)
        return PersonaFileResult.Success(saved)
    }

    fun removeFile(fileId: String, userId: String): PersonaFileResult {
        val file = personaFileRepository.findById(fileId).orElse(null)
            ?: return PersonaFileResult.NotFound

        personaFileChunkRepository.deleteByFileId(fileId)
        personaFileRepository.deleteById(fileId)

        log.info("[persona-file] Removed file {} from persona {} by {}", fileId, file.personaId, userId)
        return PersonaFileResult.Success(file)
    }

    fun indexFileContent(fileId: String, content: String): PersonaFileResult {
        val file = personaFileRepository.findById(fileId).orElse(null)
            ?: return PersonaFileResult.NotFound

        try {
            personaFileChunkRepository.deleteByFileId(fileId)

            val chunks = chunkContent(content, MAX_CHUNK_SIZE)
            val now = LocalDateTime.now(ZoneOffset.UTC)

            chunks.forEachIndexed { index, chunkContent ->
                val chunk = PersonaFileChunk(
                    fileId = fileId,
                    personaId = file.personaId,
                    chunkIndex = index,
                    content = chunkContent,
                    tokenCount = estimateTokens(chunkContent),
                    createdAt = now
                )
                personaFileChunkRepository.save(chunk)
            }

            val updatedFile = file.copy(
                status = FileIndexStatus.INDEXED,
                chunkCount = chunks.size,
                lastError = null,
                updatedAt = now
            )
            val saved = personaFileRepository.save(updatedFile)

            log.info("[persona-file] Indexed file {} with {} chunks", fileId, chunks.size)
            return PersonaFileResult.Success(saved)
        } catch (e: Exception) {
            log.error("[persona-file] Failed to index file {}: {}", fileId, e.message)
            val failedFile = file.copy(
                status = FileIndexStatus.FAILED,
                lastError = e.message,
                updatedAt = LocalDateTime.now(ZoneOffset.UTC)
            )
            personaFileRepository.save(failedFile)
            return PersonaFileResult.Error("Failed to index: ${e.message}")
        }
    }

    fun getFileContext(personaId: String, maxTokens: Int = 8000): String {
        val files = personaFileRepository.findByPersonaIdAndStatus(personaId, FileIndexStatus.INDEXED)
        if (files.isEmpty()) return ""

        val chunks = personaFileChunkRepository.findByPersonaId(personaId)
        if (chunks.isEmpty()) return ""

        val contextBuilder = StringBuilder()
        var currentTokens = 0

        val sortedChunks = chunks.sortedBy { it.chunkIndex }

        for (chunk in sortedChunks) {
            val chunkTokens = chunk.tokenCount ?: estimateTokens(chunk.content)
            if (currentTokens + chunkTokens > maxTokens) break

            if (contextBuilder.isNotEmpty()) {
                contextBuilder.append("\n\n---\n\n")
            }

            val file = files.find { it.id == chunk.fileId }
            if (file != null && chunk.chunkIndex == 0) {
                contextBuilder.append("[From: ${file.name}]\n")
            }
            contextBuilder.append(chunk.content)
            currentTokens += chunkTokens
        }

        return contextBuilder.toString()
    }

    private fun chunkContent(content: String, maxChunkSize: Int): List<String> {
        if (content.length <= maxChunkSize) {
            return listOf(content)
        }

        val chunks = mutableListOf<String>()
        val paragraphs = content.split("\n\n")
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 2 > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                if (paragraph.length > maxChunkSize) {
                    val words = paragraph.split(" ")
                    for (word in words) {
                        if (currentChunk.length + word.length + 1 > maxChunkSize) {
                            chunks.add(currentChunk.toString().trim())
                            currentChunk = StringBuilder()
                        }
                        if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                        currentChunk.append(word)
                    }
                } else {
                    currentChunk.append(paragraph)
                }
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(paragraph)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 4.0).toInt()
    }
}
