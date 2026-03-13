package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.Persona
import com.atxbogart.trustedadvisor.model.PersonaFile
import com.atxbogart.trustedadvisor.service.AddFileRequest
import com.atxbogart.trustedadvisor.service.PersonaFileResult
import com.atxbogart.trustedadvisor.service.PersonaFileService
import com.atxbogart.trustedadvisor.service.PersonaService
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/personas")
@CrossOrigin(origins = ["http://localhost:3000"])
class PersonaController(
    private val service: PersonaService,
    private val fileService: PersonaFileService,
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean
) {

    @GetMapping
    fun list(): ResponseEntity<List<Persona>> =
        try {
            ResponseEntity.ok(service.findAll())
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }

    @PostMapping
    fun create(@RequestBody persona: Persona): Persona = service.save(persona)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Persona?> = 
        service.findById(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody persona: Persona): ResponseEntity<Persona> = 
        service.findById(id)?.let { 
            val updated = persona.copy(id = id)
            ResponseEntity.ok(service.save(updated))
        } ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        service.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{personaId}/files")
    fun listFiles(@PathVariable personaId: String): ResponseEntity<PersonaFilesResponse> {
        return when (val result = fileService.getFilesForPersona(personaId)) {
            is PersonaFileResult.FileList -> ResponseEntity.ok(
                PersonaFilesResponse(files = result.files.map { it.toView() })
            )
            is PersonaFileResult.PersonaNotFound -> ResponseEntity.notFound().build()
            else -> ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{personaId}/files")
    fun addFile(
        @PathVariable personaId: String,
        @RequestBody request: AddFileRequest,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<PersonaFileResponse> {
        val userId = getCurrentUserId(principal)
        return when (val result = fileService.addFile(personaId, request, userId)) {
            is PersonaFileResult.Success -> ResponseEntity.ok(
                PersonaFileResponse(success = true, message = "File added", file = result.file.toView())
            )
            is PersonaFileResult.PersonaNotFound -> ResponseEntity.notFound().build()
            is PersonaFileResult.AlreadyExists -> ResponseEntity.ok(
                PersonaFileResponse(success = false, message = "File already attached to this persona", file = null)
            )
            is PersonaFileResult.Error -> ResponseEntity.badRequest().body(
                PersonaFileResponse(success = false, message = result.message, file = null)
            )
            else -> ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{personaId}/files/{fileId}")
    fun removeFile(
        @PathVariable personaId: String,
        @PathVariable fileId: String,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<PersonaFileResponse> {
        val userId = getCurrentUserId(principal)
        return when (val result = fileService.removeFile(fileId, userId)) {
            is PersonaFileResult.Success -> ResponseEntity.ok(
                PersonaFileResponse(success = true, message = "File removed", file = result.file.toView())
            )
            is PersonaFileResult.NotFound -> ResponseEntity.notFound().build()
            else -> ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{personaId}/files/{fileId}/index")
    fun indexFile(
        @PathVariable personaId: String,
        @PathVariable fileId: String,
        @RequestBody request: IndexFileRequest
    ): ResponseEntity<PersonaFileResponse> {
        return when (val result = fileService.indexFileContent(fileId, request.content)) {
            is PersonaFileResult.Success -> ResponseEntity.ok(
                PersonaFileResponse(success = true, message = "File indexed", file = result.file.toView())
            )
            is PersonaFileResult.NotFound -> ResponseEntity.notFound().build()
            is PersonaFileResult.Error -> ResponseEntity.badRequest().body(
                PersonaFileResponse(success = false, message = result.message, file = null)
            )
            else -> ResponseEntity.badRequest().build()
        }
    }

    private fun getCurrentUserId(principal: ApiKeyPrincipal?): String {
        return principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else "unknown"
    }

    private fun currentEmailFromOAuth2(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal as? OAuth2User ?: return null
        val attrs = principal.attributes
        return (attrs["email"] as? String)
            ?: (attrs["login"] as? String)?.let { "$it@github.local" }
    }

    private fun PersonaFile.toView() = PersonaFileView(
        id = id ?: "",
        personaId = personaId,
        sourceType = sourceType.name,
        sourceFileId = sourceFileId,
        sourceUrl = sourceUrl,
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        status = status.name,
        lastError = lastError,
        chunkCount = chunkCount,
        createdBy = createdBy,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}

data class PersonaFileView(
    val id: String,
    val personaId: String,
    val sourceType: String,
    val sourceFileId: String,
    val sourceUrl: String?,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val status: String,
    val lastError: String?,
    val chunkCount: Int,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String
)

data class PersonaFilesResponse(
    val files: List<PersonaFileView>
)

data class PersonaFileResponse(
    val success: Boolean,
    val message: String,
    val file: PersonaFileView?
)

data class IndexFileRequest(
    val content: String
)