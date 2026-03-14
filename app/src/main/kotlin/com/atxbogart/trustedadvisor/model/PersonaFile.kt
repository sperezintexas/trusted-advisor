package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class FileSourceType {
    GOOGLE_DRIVE,
    UPLOAD,
    URL
}

enum class FileIndexStatus {
    PENDING,
    INDEXING,
    INDEXED,
    FAILED
}

@Document(collection = "personaFiles")
@CompoundIndex(name = "persona_source_idx", def = "{'personaId': 1, 'sourceFileId': 1}", unique = true)
data class PersonaFile(
    @Id
    val id: String? = null,
    @Indexed
    val personaId: String,
    val sourceType: FileSourceType = FileSourceType.GOOGLE_DRIVE,
    val sourceFileId: String,
    val sourceUrl: String? = null,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val status: FileIndexStatus = FileIndexStatus.PENDING,
    val lastError: String? = null,
    val pendingContent: String? = null,
    val checksum: String? = null,
    val chunkCount: Int = 0,
    val createdBy: String,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)

@Document(collection = "personaFileChunks")
@CompoundIndex(name = "file_chunk_idx", def = "{'fileId': 1, 'chunkIndex': 1}", unique = true)
data class PersonaFileChunk(
    @Id
    val id: String? = null,
    @Indexed
    val fileId: String,
    val personaId: String,
    val chunkIndex: Int,
    val content: String,
    val tokenCount: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
