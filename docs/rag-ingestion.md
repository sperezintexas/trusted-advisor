# RAG document ingestion (admin)

Admin-only workflow to upload and index text documents per persona for use as RAG context in chat.

## Overview

- **Where**: Config → **RAG Documents** tab (`/config`, then select the "RAG Documents" tab).
- **Who**: Admin users only (same as Access Requests).
- **Flow**: Upload .pdf, .txt, or .md files for a persona → backend extracts text (PDF via PDFBox), chunks and indexes → chat uses indexed chunks as context when that persona is selected.

## Backend

- **Chunking**: Server-side only. Backend uses a deterministic policy: character budget (default 4000), paragraph-aware splitting, and 200-character overlap between chunks.
- **Storage**: `personaFiles` (metadata, status, chunk count) and `personaFileChunks` (content, token count, order).
- **Retrieval**: `PersonaFileService.getFileContext(personaId, maxTokens)` returns concatenated content from **INDEXED** files only; chunks are filtered to the selected persona and token-limited.

## Admin API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/personas/{personaId}/documents` | List documents (name, status, chunk count, updated time). |
| POST | `/api/admin/personas/{personaId}/documents` | Upload a file (multipart `file`). Accepts PDF (application/pdf), text/plain, text/markdown; UTF-8 for text. |
| POST | `/api/admin/personas/{personaId}/documents/{docId}/index` | Reindex: optional body `{ "content": "..." }` to reindex with new content; no body to re-chunk from existing stored chunks. |
| DELETE | `/api/admin/personas/{personaId}/documents/{docId}` | Delete document and its chunks. |

## Config UI

1. Open **Configuration** → **RAG Documents**.
2. Select a **persona** (e.g. fintech advisor). Default is the first persona with "fintech" or "advisor" in the name, or the first in the list.
3. **Upload file**: choose a .pdf, .txt, or .md file. Text is extracted (PDFs via Apache PDFBox) and then chunked and indexed immediately.
4. **Table**: name, status (PENDING / INDEXED / FAILED), chunk count, indexed time, **Reindex** (re-run chunking from stored chunks), **Delete**.

## Chat behavior

When the user selects a persona and sends a message:

1. Backend loads that persona’s system prompt.
2. Backend calls `getFileContext(personaId, maxFileContextTokens)` and appends the result to the system prompt as context.
3. Only chunks from files with status **INDEXED** for that persona are included; other personas’ documents are never used.

## Validation

- **Backend**: `PersonaFileChunkingTest` covers chunk boundaries, overlap, and deterministic output.
- **Manual**: Upload a fintech doc (e.g. PDF) in RAG Documents, confirm chunk count and status INDEXED, then ask a question in chat with that persona and verify answers reflect the uploaded content.

## Generate test questions (admin)

Config → **Generate Questions** tab uses the same indexed RAG context to generate multiple-choice practice questions via Grok. Questions can be tagged by **FINRA exam topic** and **saved to the practice exam pool** so that when a user starts a practice exam, the session is built from the pool following FINRA topic weight distribution.

- **Endpoint**: `POST /api/admin/personas/{personaId}/generate-questions` with body `{ "count": 10, "examCode": "SIE" | "SERIES_7" | "SERIES_57" | "SERIES_65", "saveToPool": true }` (count 1–25). If `examCode` is set, the model assigns each question a topic from that exam’s outline; if `saveToPool` is true, questions are persisted to `coachQuestions` for that exam.
- **FINRA topic weights**: Practice sessions are built by topic % (see `ExamTopicWeights`). SIE: Knowledge of Capital Markets 16%, Products and Risks 44%, Trading/Customer Accounts 31%, Regulatory Framework 9%. Series 7: four job functions (7%, 9%, 73%, 11%). Series 57: Trading 82%, Books and Records 18%. Series 65: five topic areas.
- **Flow**: Backend loads `getFileContext(personaId)`, sends to Grok with exam topic list when `examCode` is set; response includes `topic` per question. If `saveToPool`, each question is saved as `CoachQuestion` (examCode, topic, source = "generated").
- **Practice exam**: When the user starts a practice exam, `GET /api/coach/exams/{examCode}/practice-session?count=N` draws from the pool using `ExamTopicWeights.targetCountsByTopic` so the mix of questions matches FINRA distribution. If the pool is empty, the API returns no questions and the UI suggests that an admin generate and save questions.
- **Pool size**: `GET /api/coach/exams/{examCode}/pool-size` returns `{ examCode, count }` for the active question count.
- **UI**: Select persona, **Exam** (optional; enables topic tagging and Save to pool), number of questions, **Save to exam pool** (when exam selected). Generate; results show topic per question when present. **Copy JSON** copies the full list.
- **Requirement**: The persona must have at least one document indexed (RAG Documents) or the request returns an error.
