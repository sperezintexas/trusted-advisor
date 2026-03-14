package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import com.atxbogart.trustedadvisor.repository.PersonaFileChunkRepository
import com.atxbogart.trustedadvisor.repository.PersonaFileRepository
import com.atxbogart.trustedadvisor.repository.PersonaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

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
    val content: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
)

data class PersonaEvidenceChunk(
    val fileId: String,
    val fileName: String,
    val chunkIndex: Int,
    val content: String,
    val tokenCount: Int
)

data class PersonaBulkReindexResult(
    val queued: Int,
    val skipped: Int,
    val failed: Int
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
        const val CHUNK_OVERLAP_CHARS = 200
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
            pendingContent = null,
            createdBy = userId,
            createdAt = now,
            updatedAt = now
        )

        val saved = personaFileRepository.save(file)
        val savedId = saved.id
        if (!request.content.isNullOrBlank() && savedId != null) {
            return queueIndexing(savedId, request.content)
        }
        log.info("[persona-file] Added file {} to persona {} by {}", saved.id, personaId, userId)
        return PersonaFileResult.Success(saved)
    }

    fun addFileFromUpload(
        personaId: String,
        filename: String,
        bytes: ByteArray,
        contentType: String?,
        userId: String
    ): PersonaFileResult {
        if (!personaRepository.existsById(personaId)) {
            return PersonaFileResult.PersonaNotFound
        }
        val existingCount = personaFileRepository.countByPersonaId(personaId)
        if (existingCount >= MAX_FILES_PER_PERSONA) {
            return PersonaFileResult.Error("Maximum files per persona ($MAX_FILES_PER_PERSONA) reached")
        }
        val extraction = extractTextFromBytes(bytes, contentType)
        val content = when (extraction) {
            is TextExtraction.Success -> extraction.content
            is TextExtraction.Error -> return PersonaFileResult.Error(extraction.message)
        }
        val sourceFileId = "upload-${UUID.randomUUID()}"
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val file = PersonaFile(
            personaId = personaId,
            sourceType = FileSourceType.UPLOAD,
            sourceFileId = sourceFileId,
            sourceUrl = null,
            name = filename,
            mimeType = contentType,
            sizeBytes = bytes.size.toLong(),
            status = FileIndexStatus.PENDING,
            pendingContent = null,
            createdBy = userId,
            createdAt = now,
            updatedAt = now
        )
        val saved = personaFileRepository.save(file)
        val savedId = saved.id ?: return PersonaFileResult.Error("Failed to persist uploaded file ID")
        return queueIndexing(savedId, content)
    }

    private sealed class TextExtraction {
        data class Success(val content: String) : TextExtraction()
        data class Error(val message: String) : TextExtraction()
    }

    private fun extractTextFromBytes(bytes: ByteArray, contentType: String?): TextExtraction {
        if (bytes.isEmpty()) {
            return TextExtraction.Error("File is empty")
        }
        val mime = contentType?.lowercase()?.substringBefore(';')?.trim() ?: ""
        return when {
            mime == "application/pdf" || contentType == null && bytes.take(4) == listOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte()) ->
                extractTextFromPdf(bytes)
            mime.startsWith("text/plain") ||
                mime.startsWith("text/markdown") ||
                mime == "text/md" ->
                decodeUtf8(bytes)
            else -> {
                if (looksBinary(bytes)) {
                    TextExtraction.Error("Unsupported binary file. Use PDF, text/plain, or text/markdown.")
                } else {
                    decodeUtf8(bytes)
                }
            }
        }
    }

    private fun decodeUtf8(bytes: ByteArray): TextExtraction {
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            if (text.isBlank()) {
                TextExtraction.Error("Extracted text is empty; cannot index file.")
            } else {
                TextExtraction.Success(text)
            }
        } catch (_: Exception) {
            TextExtraction.Error("Unknown binary decode. Only UTF-8 text/markdown and PDF are supported.")
        }
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = bytes.take(2048)
        val nonTextCount = sample.count { b ->
            val unsigned = b.toInt() and 0xff
            unsigned == 0 || (unsigned < 9) || (unsigned in 14..31)
        }
        return nonTextCount > sample.size / 10
    }

    private fun extractTextFromPdf(bytes: ByteArray): TextExtraction {
        return try {
            Loader.loadPDF(bytes).use { doc ->
                val text = PDFTextStripper().getText(doc)
                if (text.isBlank()) {
                    TextExtraction.Error("PDF text extraction returned empty content; file cannot be indexed.")
                } else {
                    TextExtraction.Success(text)
                }
            }
        } catch (e: Exception) {
            log.warn("[persona-file] PDF text extraction failed: {}", e.message)
            TextExtraction.Error("Failed to extract PDF text. Ensure the PDF is not image-only or encrypted.")
        }
    }

    fun removeFile(fileId: String, userId: String): PersonaFileResult {
        val file = personaFileRepository.findById(fileId).orElse(null)
            ?: return PersonaFileResult.NotFound

        personaFileChunkRepository.deleteByFileId(fileId)
        personaFileRepository.deleteById(fileId)

        log.info("[persona-file] Removed file {} from persona {} by {}", fileId, file.personaId, userId)
        return PersonaFileResult.Success(file)
    }

    /** Reindex by re-chunking existing stored chunks (no new content). */
    fun reindexFromStoredChunks(fileId: String): PersonaFileResult {
        if (!personaFileRepository.existsById(fileId)) return PersonaFileResult.NotFound
        val chunks = personaFileChunkRepository.findByFileIdOrderByChunkIndexAsc(fileId)
        if (chunks.isEmpty()) {
            return PersonaFileResult.Error("No chunks to reindex; upload content first.")
        }
        val content = chunks.joinToString("\n\n") { it.content }
        return queueIndexing(fileId, content)
    }

    fun queueReindexAllForPersona(personaId: String): PersonaBulkReindexResult? {
        if (!personaRepository.existsById(personaId)) return null
        val files = personaFileRepository.findByPersonaId(personaId)
        var queued = 0
        var skipped = 0
        var failed = 0

        files.forEach { file ->
            val fileId = file.id
            if (fileId.isNullOrBlank()) {
                failed++
                return@forEach
            }
            val pendingContent = file.pendingContent
            if (!pendingContent.isNullOrBlank()) {
                when (queueIndexing(fileId, pendingContent)) {
                    is PersonaFileResult.Success -> queued++
                    else -> failed++
                }
                return@forEach
            }
            val chunks = personaFileChunkRepository.findByFileIdOrderByChunkIndexAsc(fileId)
            if (chunks.isEmpty()) {
                skipped++
                return@forEach
            }
            val content = chunks.joinToString("\n\n") { it.content }.trim()
            if (content.isBlank()) {
                skipped++
                return@forEach
            }
            when (queueIndexing(fileId, content)) {
                is PersonaFileResult.Success -> queued++
                else -> failed++
            }
        }

        return PersonaBulkReindexResult(
            queued = queued,
            skipped = skipped,
            failed = failed
        )
    }

    fun queueIndexing(fileId: String, content: String): PersonaFileResult {
        val file = personaFileRepository.findById(fileId).orElse(null)
            ?: return PersonaFileResult.NotFound
        if (content.isBlank()) {
            return PersonaFileResult.Error("Extracted text is empty; cannot queue indexing.")
        }
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated = file.copy(
            status = FileIndexStatus.PENDING,
            pendingContent = content,
            lastError = null,
            updatedAt = now
        )
        val saved = personaFileRepository.save(updated)
        log.info("[persona-file] Queued indexing for file {}", fileId)
        return PersonaFileResult.Success(saved)
    }

    fun processNextPendingFile() {
        val pending = personaFileRepository.findTop20ByStatusOrderByUpdatedAtAsc(FileIndexStatus.PENDING)
            .firstOrNull { !it.pendingContent.isNullOrBlank() }
            ?: return
        val fileId = pending.id ?: return
        val now = LocalDateTime.now(ZoneOffset.UTC)
        personaFileRepository.save(
            pending.copy(
                status = FileIndexStatus.INDEXING,
                lastError = null,
                updatedAt = now
            )
        )
        val result = indexFileContent(fileId, pending.pendingContent ?: "")
        if (result is PersonaFileResult.Error) {
            log.warn("[persona-file] Async indexing failed for {}: {}", fileId, result.message)
        }
    }

    fun indexFileContent(fileId: String, content: String): PersonaFileResult {
        val file = personaFileRepository.findById(fileId).orElse(null)
            ?: return PersonaFileResult.NotFound

        try {
            personaFileChunkRepository.deleteByFileId(fileId)

            val chunks = chunkContent(content, MAX_CHUNK_SIZE, CHUNK_OVERLAP_CHARS)
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
                pendingContent = null,
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
                pendingContent = null,
                updatedAt = LocalDateTime.now(ZoneOffset.UTC)
            )
            personaFileRepository.save(failedFile)
            return PersonaFileResult.Error("Failed to index: ${e.message}")
        }
    }

    /**
     * Returns concatenated content from indexed persona file chunks for RAG context.
     * Only chunks belonging to files with status INDEXED for this persona are included.
     */
    fun getFileContext(personaId: String, maxTokens: Int = 8000): String {
        val files = personaFileRepository.findByPersonaIdAndStatus(personaId, FileIndexStatus.INDEXED)
        if (files.isEmpty()) return ""

        val indexedFileIds = files.map { it.id!! }.toSet()
        val chunks = personaFileChunkRepository.findByPersonaId(personaId)
            .filter { it.fileId in indexedFileIds }
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

    /**
     * Query-aware retrieval of top-k persona chunks.
     * Uses lexical scoring with token overlap and phrase bonus.
     */
    fun getTopChunksForQuery(
        personaId: String,
        query: String,
        topK: Int = 8,
        maxTokens: Int = 4000
    ): List<PersonaEvidenceChunk> {
        val files = personaFileRepository.findByPersonaIdAndStatus(personaId, FileIndexStatus.INDEXED)
        if (files.isEmpty()) return emptyList()

        val indexedFileIds = files.mapNotNull { it.id }.toSet()
        if (indexedFileIds.isEmpty()) return emptyList()

        val fileNameById = files.mapNotNull { file -> file.id?.let { id -> id to file.name } }.toMap()
        val queryTokens = tokenizeForSearch(query)
        if (queryTokens.isEmpty()) return emptyList()

        val ranked = personaFileChunkRepository.findByPersonaId(personaId)
            .asSequence()
            .filter { it.fileId in indexedFileIds }
            .map { chunk ->
                val tokenCount = chunk.tokenCount ?: estimateTokens(chunk.content)
                val score = scoreChunk(query, queryTokens, chunk.content)
                Triple(chunk, score, tokenCount)
            }
            .filter { (_, score, _) -> score > 0.0 }
            .sortedWith(
                compareByDescending<Triple<PersonaFileChunk, Double, Int>> { it.second }
                    .thenBy { it.first.chunkIndex }
            )
            .toList()

        if (ranked.isEmpty()) return emptyList()

        val selected = mutableListOf<PersonaEvidenceChunk>()
        var usedTokens = 0
        for ((chunk, _, tokenCount) in ranked) {
            if (selected.size >= topK) break
            if (usedTokens + tokenCount > maxTokens) continue
            selected.add(
                PersonaEvidenceChunk(
                    fileId = chunk.fileId,
                    fileName = fileNameById[chunk.fileId] ?: "Unknown file",
                    chunkIndex = chunk.chunkIndex,
                    content = chunk.content,
                    tokenCount = tokenCount
                )
            )
            usedTokens += tokenCount
        }
        return selected
    }

    /**
     * Splits content into chunks with a character budget and optional overlap.
     * Overlap keeps context across boundaries (deterministic for same input).
     */
    internal fun chunkContent(content: String, maxChunkSize: Int, overlapChars: Int = 0): List<String> {
        if (content.length <= maxChunkSize) {
            return listOf(content)
        }

        val chunks = mutableListOf<String>()
        val paragraphs = content.split("\n\n")
        var currentChunk = StringBuilder()
        val effectiveOverlap = overlapChars.coerceIn(0, maxChunkSize / 2)

        fun flushChunk() {
            val text = currentChunk.toString().trim()
            if (text.isEmpty()) return
            chunks.add(text)
            if (effectiveOverlap > 0 && text.length > effectiveOverlap) {
                val overlapStart = text.length - effectiveOverlap
                var start = overlapStart
                while (start > 0 && text[start - 1].isLetterOrDigit()) start--
                currentChunk = StringBuilder(if (start < text.length) text.substring(start) else "")
            } else {
                currentChunk = StringBuilder()
            }
        }

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 2 > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    flushChunk()
                }
                if (paragraph.length > maxChunkSize) {
                    val words = paragraph.split(" ")
                    for (word in words) {
                        if (currentChunk.isNotEmpty() && currentChunk.length + word.length + 1 > maxChunkSize) {
                            flushChunk()
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

    private fun scoreChunk(query: String, queryTokens: Set<String>, content: String): Double {
        val lowered = content.lowercase()
        val contentTokens = tokenizeForSearch(content)
        if (contentTokens.isEmpty()) return 0.0

        var overlapHits = 0
        queryTokens.forEach { token ->
            if (token in contentTokens) overlapHits++
        }
        if (overlapHits == 0) return 0.0

        val phraseBonus = if (lowered.contains(query.lowercase().trim())) 2.0 else 0.0
        val overlapRatio = overlapHits.toDouble() / queryTokens.size.toDouble()
        val densityBoost = overlapHits.toDouble() / 3.0
        return overlapRatio * 4.0 + densityBoost + phraseBonus
    }

    private fun tokenizeForSearch(text: String): Set<String> {
        val stopWords = setOf(
            "the", "and", "for", "that", "with", "this", "from", "are", "you", "your",
            "have", "has", "was", "were", "what", "when", "where", "why", "how", "can",
            "could", "would", "should", "about", "into", "than", "then", "them", "they",
            "their", "there", "here", "also", "any", "all", "use", "using", "used"
        )
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { it !in stopWords }
            .take(128)
            .toSet()
    }
}
