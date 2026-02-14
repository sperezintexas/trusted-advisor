# Database schema

## Trusted Advisor app collections

Used by the Kotlin backend (Spring Data MongoDB). No validators are applied by default; the app relies on model types.

| Collection | Purpose |
|------------|---------|
| **personas** | Custom chat personas (name, systemPrompt, webSearchEnabled, yahooFinanceEnabled). |
| **chatHistory** | Per-user chat messages (`userId`, `messages`, `updatedAt`). |
| **coachExams** | Exam metadata (code, name, version, totalQuestionsInOutline). |
| **coachQuestions** | Multiple-choice questions (`examCode`, question, choices, correctLetter, explanation, topic, difficulty, active). |
| **coachUserProgress** | Per-user progress per exam (`userId`, `examCode`, totalAsked, correct, weakTopics). |
| **coachExamAttempts** | Saved exam results (`userId`, `examCode`, correct, total, percentage, passed, completedAt). |
| **coachSessions** | Optional session state. |

See `app/src/main/kotlin/.../model/*.kt` for exact field names and types.

---

## Optional: schema validators (legacy/other)

The sections below describe validators for collections that may be used by other scripts or a different app surface. Validators are applied by the one-time script [scripts/mongo-validators.ts](../scripts/mongo-validators.ts) (if present).

### How to run the validator setup

1. Set environment variables:
   - `MONGODB_URI` (or `MONGODB_URI_B64` for base64-encoded URI)
   - `MONGODB_DB` (default: `myinvestments`)
2. Run:
   ```bash
   npx tsx scripts/mongo-validators.ts
   ```
3. For existing collections, the script runs `collMod` to add the validator. **Ensure existing documents satisfy the schema** before running, or fix/migrate them first.

## Collection schemas (required fields)

### alerts

| Field           | Type   | Required |
|----------------|--------|----------|
| symbol         | string | yes      |
| recommendation | string | yes      |
| reason         | string | yes      |
| createdAt      | string | yes      |
| acknowledged   | bool   | yes      |
| accountId      | string | no       |
| type           | string | no       |
| severity       | string | no       |
| metrics        | object | no       |
| details        | object | no       |
| riskWarning    | string | no       |
| suggestedActions | array | no     |
| watchlistItemId | string | no      |
| positionId     | string | no       |
| source         | string | no       |
| deliveryStatus | object | no       |
| acknowledgedAt | string | no       |

### optionRecommendations

| Field            | Type   | Required |
|-----------------|--------|----------|
| positionId      | string | yes      |
| accountId       | string | yes      |
| symbol          | string | yes      |
| underlyingSymbol| string | yes      |
| strike          | number | yes      |
| expiration      | string | yes      |
| optionType      | "call" \| "put" | yes |
| contracts       | number | yes      |
| recommendation  | "HOLD" \| "BUY_TO_CLOSE" | yes |
| reason          | string | yes      |
| metrics         | object | yes      |
| createdAt       | string | yes      |
| storedAt        | string | no       |
| source          | string | no       |

### coveredCallRecommendations

| Field           | Type   | Required |
|----------------|--------|----------|
| accountId      | string | yes      |
| symbol         | string | yes      |
| source         | "holdings" \| "watchlist" | yes |
| recommendation | "HOLD" \| "BUY_TO_CLOSE" \| "SELL_NEW_CALL" \| "ROLL" \| "NONE" | yes |
| confidence     | "HIGH" \| "MEDIUM" \| "LOW" | yes |
| reason         | string | yes      |
| metrics        | object | yes      |
| createdAt      | string | yes      |
| storedAt       | string | no       |

### protectivePutRecommendations

| Field           | Type   | Required |
|----------------|--------|----------|
| accountId      | string | yes      |
| symbol         | string | yes      |
| recommendation | "HOLD" \| "SELL_TO_CLOSE" \| "ROLL" \| "BUY_NEW_PUT" \| "NONE" | yes |
| confidence     | "HIGH" \| "MEDIUM" \| "LOW" | yes |
| reason         | string | yes      |
| metrics        | object | yes      |
| createdAt      | string | yes      |
| storedAt       | string | no       |

### straddleStrangleRecommendations

| Field           | Type   | Required |
|----------------|--------|----------|
| accountId      | string | yes      |
| symbol         | string | yes      |
| isStraddle     | bool   | yes      |
| recommendation | "HOLD" \| "SELL_TO_CLOSE" \| "ROLL" \| "ADD" \| "NONE" | yes |
| confidence     | "HIGH" \| "MEDIUM" \| "LOW" | yes |
| reason         | string | yes      |
| metrics        | object | yes      |
| createdAt      | string | yes      |
| storedAt       | string | no       |

### priceCache (holdings price refresh job)

Populated by `refreshHoldingsPrices` job. No validator applied. One doc per stock symbol.

| Field        | Type   | Required |
|-------------|--------|----------|
| symbol      | string | yes      |
| price       | number | yes      |
| change      | number | yes      |
| changePercent | number | yes    |
| updatedAt   | string | yes      |

### optionPriceCache (holdings price refresh job)

Populated by `refreshHoldingsPrices` job. No validator applied. One doc per (symbol, expiration, strike, optionType).

| Field      | Type   | Required |
|-----------|--------|----------|
| symbol    | string | yes      |
| expiration| string | yes      |
| strike    | number | yes      |
| optionType| "call" \| "put" | yes |
| price     | number | yes      |
| updatedAt | string | yes      |

Types in application code: [src/types/portfolio.ts](../src/types/portfolio.ts) (Option, CoveredCall, ProtectivePut); [src/lib/straddle-strangle-analyzer.ts](../src/lib/straddle-strangle-analyzer.ts) (StraddleStrangle).
