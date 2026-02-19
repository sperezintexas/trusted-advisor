---
name: repo-review-best-practices
version: "1.0.0"
description: Review the repository and suggest best practices or improvements. Use when the user asks for a repo review, code review, best practices, or improvements.
---

# Repo Review & Best Practices

Perform a structured review of the codebase and suggest actionable best practices or improvements. Follow this workflow.

## 1. Scope the Review

- **Breadth:** Prefer a full-repo pass unless the user limits scope (e.g. "frontend only", "API layer").
- **Sources of truth:** Read `AGENTS.md`, `.cursor/rules/`, and key config (e.g. `application.yml`, `package.json`, `tsconfig.json`) to align with project conventions.

## 2. Review Dimensions

Cover these areas; call out only **actionable** findings (no generic fluff).

| Area | What to check |
|------|----------------|
| **Architecture** | Layering (controller → service → repo), separation of concerns, duplication, dead code. |
| **TypeScript / Next.js** | Type safety (`any` misuse), Server vs Client Components, App Router usage, lint/typecheck hygiene. |
| **Kotlin / Spring** | Controller/service/repo boundaries, immutability, DTOs, error handling, alignment with project rules (e.g. Arrow-KT if present). |
| **Security** | Secrets handling, auth boundaries, input validation, rate limits, injection risks. |
| **Data / API** | MongoDB usage, API contracts, backward compatibility, error responses. |
| **Testing & quality** | Test coverage gaps, validation commands (Gradle, npm), pre-commit/CI. |
| **Docs & DX** | AGENTS.md accuracy, README, env setup, run/validation commands. |

## 3. Output Format

1. **Summary (2–4 bullets)** — Overall health and top priorities.
2. **Findings** — Grouped by dimension. Each item: **What** (concise) → **Where** (file/area) → **Suggestion** (concrete).
3. **Suggested next steps** — Ordered by impact (e.g. security first, then reliability, then DX). One line each; no implementation unless the user asks.

## 4. Constraints

- Do not change code unless the user asks for fixes.
- Respect project rules (AGENTS.md, .cursor/rules); suggest improvements that fit the stack (Next.js, Kotlin/Spring, MongoDB).
- Prefer high-signal, low-noise: skip nitpicks unless they affect correctness, security, or maintainability.
