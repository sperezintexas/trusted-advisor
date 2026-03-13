package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.repository.PersonaFileChunkRepository
import com.atxbogart.trustedadvisor.repository.PersonaFileRepository
import com.atxbogart.trustedadvisor.repository.PersonaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PersonaFileChunkingTest {

    private val personaFileRepository = mock(PersonaFileRepository::class.java)
    private val personaFileChunkRepository = mock(PersonaFileChunkRepository::class.java)
    private val personaRepository = mock(PersonaRepository::class.java)

    private val service = PersonaFileService(
        personaFileRepository = personaFileRepository,
        personaFileChunkRepository = personaFileChunkRepository,
        personaRepository = personaRepository
    )

    @Test
    fun `chunkContent returns single chunk when content under max size`() {
        val content = "Short text"
        val chunks = service.chunkContent(content, 4000, 0)
        assertEquals(1, chunks.size)
        assertEquals(content, chunks[0])
    }

    @Test
    fun `chunkContent splits by paragraph boundary when over max size`() {
        val p1 = "a".repeat(2000)
        val p2 = "b".repeat(2000)
        val content = "$p1\n\n$p2"
        val chunks = service.chunkContent(content, 2500, 0)
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].length <= 2500)
        assertTrue(chunks[1].length <= 2500)
        assertTrue(chunks[0].contains("a"))
        assertTrue(chunks[1].contains("b"))
    }

    @Test
    fun `chunkContent is deterministic for same input`() {
        val content = "First paragraph.\n\nSecond paragraph with more text.\n\nThird."
        val maxSize = 30
        val chunks1 = service.chunkContent(content, maxSize, 0)
        val chunks2 = service.chunkContent(content, maxSize, 0)
        assertEquals(chunks1.size, chunks2.size)
        chunks1.zip(chunks2).forEach { (a, b) -> assertEquals(a, b) }
    }

    @Test
    fun `chunkContent with overlap produces overlapping boundaries`() {
        val content = "alpha " + "word ".repeat(200) + "beta"
        val maxSize = 100
        val overlap = 20
        val chunks = service.chunkContent(content, maxSize, overlap)
        assertTrue(chunks.size >= 2)
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= maxSize + overlap, "chunk length ${chunk.length}")
        }
    }

    @Test
    fun `chunkContent with zero overlap keeps chunks within budget`() {
        val content = "x ".repeat(500)
        val maxSize = 200
        val chunks = service.chunkContent(content, maxSize, 0)
        assertTrue(chunks.size >= 2)
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= maxSize + 50, "chunk length ${chunk.length} should be <= ${maxSize + 50}")
        }
    }
}
