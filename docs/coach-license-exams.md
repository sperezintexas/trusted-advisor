# Coach: License Exam Preparation

The Coach module provides timed, multiple-choice practice and full exams for FINRA securities licensing exams. Users can check answers per question (with correct/incorrect highlighting and optional explanation), then submit to see a score and pass/fail, or cancel without saving.

## Supported exams

| Code       | Name                              | Full exam              | Time limit (full) | Passing % |
|-----------|------------------------------------|------------------------|-------------------|-----------|
| **SIE**   | Securities Industry Essentials     | 75 questions           | 105 min           | 70%       |
| **SERIES_7** | General Securities Representative | 125 questions       | 225 min           | 72%       |
| **SERIES_57** | Securities Trader               | 50 questions           | 90 min            | 70%       |

Exam codes in URLs and APIs: `SIE`, `SERIES_7`, `SERIES_57` (alternatives `SERIES7`, `SERIES57` are accepted).

## User flow

1. **Exam list** — User goes to `/coach`. Sees one card per exam (name, time limit). Each card offers:
   - **Practice exam** — Starts a timed session with a subset of questions (e.g. 20 for quick practice).
   - **Tutor session** (SIE only) — Opens Chat with the “Options Exam Coach” persona and a pre-filled SIE study prompt.

2. **Choose format** — On `/coach/[examCode]` (e.g. `/coach/SIE`):
   - **Practice (random)** — Fixed number of questions (e.g. 20), time scaled to that count.
   - **Full exam** — Full question count and full time limit for that exam.

3. **During the exam**
   - One question at a time; multiple choice A/B/C/D.
   - User selects an answer (selection is highlighted).
   - **Check Answer** — Calls the backend to validate the selection. Correct choice turns **green**; incorrect selection turns **amber**, and the correct choice is also shown in green. Answer choices are then locked for that question.
   - **Show Explanation** — After checking, user can reveal the rationale (stored with the question).
   - **Previous / Next** — Navigate between questions. Progress bar and question nav dots show position.
   - **Timer** — Countdown; when time hits zero the exam does not auto-submit (user can still Submit or Cancel).
   - **Cancel exam** — Exits without saving; no attempt or score is recorded.
   - **Submit exam** — Sends all answers to the backend for scoring; user can choose to save the attempt (stored per `userId`).

4. **Results** — After submit, user sees score (correct/total, percentage), pass/fail, and passing threshold. Options: “Back to exam list”, “Take another practice exam”.

5. **History** — From the exam start screen or `/coach/history`: list of past attempts (per exam or all). Each attempt shows passed/failed, score, and date.

## API reference

Base path: `/api/coach`. All responses JSON.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/exams` | List exam metadata (code, name, version, totalQuestionsInOutline). |
| GET | `/exams/{examCode}/practice-session?count=N` | Get a practice session: N random questions for that exam (no correct answer or explanation in response). Returns `{ questions, totalMinutes }`. |
| GET | `/exams/{examCode}/check?questionId=&selectedLetter=` | Check a single answer. Returns `{ correct, correctLetter, explanation }`. Does **not** record progress. |
| POST | `/exams/{examCode}/score` | Score an exam. Body: `{ answers: [{ questionId, selectedLetter }], userId?, save? }`. Returns `{ correct, total, percentage, passed, passingPercentage }`. If `save === true` and `userId` is set, creates a `CoachExamAttempt` and updates `CoachUserProgress`. |
| GET | `/history?userId=` | All attempt history for the user. |
| GET | `/exams/{examCode}/history?userId=` | Attempt history for that exam only. |
| GET | `/progress/{examCode}?userId=` | User progress for that exam (totalAsked, correct, weakTopics). |
| GET | `/questions/{examCode}?excludeIds=` | Single random question (for other flows); returns full question including correctLetter and explanation. |
| POST | `/answers/{examCode}` | Record a single answer (for non–practice-session flows). Body: `{ userId, questionId, selectedLetter }`. Returns `{ correct }`. Updates `CoachUserProgress` and weak topics. |

### Practice session response (no answers)

Session questions are intentionally minimal so the client cannot show answers before “Check Answer”:

```ts
{
  questions: [
    { id: string, question: string, choices: [{ letter: "A"|"B"|"C"|"D", text: string }] }
  ],
  totalMinutes: number
}
```

### Check answer response

```ts
{
  correct: boolean
  correctLetter: "A" | "B" | "C" | "D"
  explanation: string
}
```

### Score request/response

Request:

```ts
{
  answers: [{ questionId: string, selectedLetter: string }]
  userId?: string
  save?: boolean
}
```

Response:

```ts
{
  correct: number
  total: number
  percentage: number
  passed: boolean
  passingPercentage: number
}
```

## Data model (MongoDB)

- **coachExams** — One document per exam (code, name, version, totalQuestionsInOutline). Seeded on first run.
- **coachQuestions** — One per question: examCode, question text, choices (letter + text), correctLetter, explanation, optional topic/difficulty/outlineReference/source, active flag.
- **coachUserProgress** — Per user per exam: totalAsked, correct, lastSessionAt, weakTopics (topic + missCount). Updated when recording answers or saving a scored attempt.
- **coachExamAttempts** — Saved attempts: userId, examCode, correct, total, percentage, passed, completedAt, createdAt.
- **coachSessions** — Optional; used if session state is persisted.

See `docs/database-schema.md` for the app collections table and `app/src/main/kotlin/.../model/*.kt` for Kotlin types.

## Frontend routes and types

- **Routes:** `/coach` (exam list), `/coach/[examCode]` (take exam), `/coach/history` (all attempts).
- **Types:** `frontend/types/coach.ts` — ExamCode, PracticeExamQuestion, PracticeSessionResponse, CheckAnswerResult, ScoreRequest/ScoreResponse, CoachExamAttempt, EXAM_CONFIG (question count + time per exam).

## SIE Tutor (Chat integration)

From the Coach SIE card, **Tutor session** links to `/chat?exam=SIE`. The Chat page:

- Loads with that query param and selects the persona named **“Options Exam Coach”** (if present).
- Pre-fills the input with a fixed SIE study prompt.

No separate “SIE persona” is required; the existing Options Exam Coach persona is used. See `.cursor/rules/sie-exam-tutor.mdc` for the prompt and behavior guidelines.

## Time limits and passing scores (backend)

Defined in `CoachService`:

- **Time (minutes):** SIE 105, SERIES_7 225, SERIES_57 90.
- **Passing percentage:** SIE 70%, SERIES_7 72%, SERIES_57 70%.

Practice sessions use the same limits; the frontend may scale time when using a subset of questions (e.g. 20-question practice uses a fraction of the full exam time).
