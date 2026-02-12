# Expert Review: Backend (Kotlin/Spring) & UI

Review against [.cursor/rules/kotlin-spring-enterprise.mdc](../.cursor/rules/kotlin-spring-enterprise.mdc) and concision goals.

---

## Backend (Kotlin + Spring)

### Current state

- **Layout:** Flat — `controller/`, `model/`, `repository/`, `service/`, `App.kt`. No `domain` / `application` / `adapter` split.
- **Controllers:** Direct delegation to `@Service`; return types are raw `ChatResponse` / `List<ChatMessage>`. No `Either`/`IO` at the edge.
- **Services:** Imperative, exception-based. `ChatService.sendMessage` and `GrokService.chat` can throw; no `Validated` for input, no `Either<DomainError, A>` for domain results.
- **Models:** Data classes only; no value classes (e.g. `UserId`, `PersonaId`), no sealed hierarchy for domain errors.

### Gaps vs enterprise FP rule

| Rule | Gap |
|------|-----|
| **Structure** | Prefer `domain/` (model, error, pure services), `application/` (port, usecase, dto), `adapter/in` (controllers), `adapter/out` (repositories), `infrastructure/`, `bootstrap/`. |
| **Error handling** | Domain & application should return `Either<DomainError, A>` or `ValidatedNel`; controllers map to `ResponseEntity` (e.g. 4xx on left). |
| **Effects** | External calls (Grok, DB) should be wrapped in `IO` or `suspend` with `Resource`/bracket; orchestration in adapter, not in “domain” services. |
| **Validation** | Request/command validation via `ValidatedNel` (e.g. message length, personaId format); avoid manual if-chains. |
| **Spring at edges** | Keep `@Service` only for infrastructure/orchestration; domain/application layers pure Kotlin + Arrow, no Spring. |

### Suggested direction (incremental)

1. **Introduce `domain/error`** — Sealed hierarchy (e.g. `ChatError`, `PersonaNotFound`, `GrokUnavailable`). Use in application layer return types.
2. **Application layer** — `SendMessageUseCase` returning `Either<ChatError, ChatResponse>`. Input validation with `ValidatedNel`. Controllers call use case and map `Left` → 400/404/503.
3. **Adapters** — Controllers in `adapter/in`; repositories and `GrokService` in `adapter/out`. Wrap Grok/DB in `IO` or suspend; run in controller or a thin orchestration bean.
4. **Value types** — Replace raw `userId` / `personaId` with value classes where it improves type safety and auditability.

No Arrow dependency until you are ready to refactor; you can still introduce `Either` (stdlib or Arrow) and sealed errors first.

---

## UI: More concise chat

### Issues with current `/chat` page

- **API-reference framing:** Request/response blocks per exchange (POST /v1/chat/completions, 200 Response, copy buttons) add noise for a product chat UI.
- **Dead controls:** Tabs “Try It Out | Example | Definition” don’t switch content; status pills (200/400/422) and “Stats” have no behavior.
- **Redundancy:** Long placeholder, duplicate copy logic per pair, heavy borders/headers. Two surfaces (home vs chat) with overlapping behavior.

### Concision principles

- **One primary action:** Type and send. Persona and example prompts are secondary, compact.
- **Minimal chrome:** No fake API labels or status pills unless you add a real “API” or “Debug” mode later.
- **Single chat surface:** Prefer one canonical chat page (e.g. `/chat`) with persona + examples; home can redirect or show a minimal launcher.

### Concrete UI changes (implemented)

- **Chat page:** Removed per-message request/response blocks, “Try It Out | Example | Definition” tabs, and 200/400/422 pills and “Stats” button. Kept: header with title + persona dropdown + config link, scrollable message list (user bubbles right, assistant left), single input + Send, collapsible “Example prompts” chips. Optional: one “Copy last reply” for power users.
- **Placeholder:** Short, single line (e.g. “Ask about stocks, portfolio, or strategies. Powered by Grok.”).
- **Consistency:** Reuse the same message-bubble and input pattern as the simpler home chat so behavior and layout align.

---

## Summary

- **Backend:** Move toward domain/application/adapter structure; use `Either`/`Validated` and keep Spring at the edges; wrap effects in `IO` when introducing Arrow.
- **UI:** Treat chat as a single, focused conversation surface: message list + input + persona + example chips; remove API-style framing and non-functional controls for a more concise UI.
