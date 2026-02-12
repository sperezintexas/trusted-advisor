'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import AppHeader from '../components/AppHeader'

export default function ConfigPage() {
  const [origin, setOrigin] = useState('')

  useEffect(() => {
    setOrigin(window.location.origin)
  }, [])

  return (
    <div
      className="flex min-h-screen flex-col bg-white"
      style={{ backgroundImage: 'none' }}
    >
      <AppHeader />
      <main className="mx-auto w-full max-w-3xl px-4 py-10">
        <h1 className="mb-2 text-2xl font-semibold text-[var(--docs-text)]">
          Configuration
        </h1>
        <p className="mb-10 text-sm text-[var(--docs-muted)]">
          Prompt and persona options. Modeled after the{' '}
          <a
            href="https://docs.x.ai/developers/api-reference"
            target="_blank"
            rel="noopener noreferrer"
            className="text-[var(--docs-accent)] hover:underline"
          >
            xAI REST API Reference
          </a>
          .
        </p>

        {/* Personas */}
        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Personas
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            Manage expert personas used in chat (finance, legal, tax, medical,
            trusted advisor).
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/personas · POST /api/personas · PUT /api/personas/:id ·
            DELETE /api/personas/:id
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Link
              href="/personas"
              className="inline-flex items-center rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)]"
            >
              Manage personas
              <span className="ml-2">→</span>
            </Link>
            <Link
              href="/chat"
              className="inline-flex items-center rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)]"
            >
              Open chat
              <span className="ml-2">→</span>
            </Link>
          </div>
        </section>

        {/* Prompt & context */}
        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Prompt & context
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            System prompt override, risk profile, and strategy goals (when chat config API is enabled).
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/chat/config · PUT /api/chat/config
          </div>
          <div className="rounded-lg border border-[var(--docs-border)] bg-white p-4 text-sm text-[var(--docs-muted)]">
            Chat config (tools, context.personaId, systemPromptOverride) can be
            saved per user. Use the persona selector and system prompt field in{' '}
            <Link
              href="/chat"
              className="text-[var(--docs-accent)] hover:underline"
            >
              Chat
            </Link>{' '}
            once config endpoints are wired.
          </div>
        </section>

        {/* Base URL reference style */}
        <section className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4">
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">
            API base
          </h3>
          <p className="docs-path text-sm">
            {origin || '[your-domain]'}/api
          </p>
          <p className="mt-2 text-xs text-[var(--docs-muted)]">
            Backend proxy: requests to /api/* are forwarded to the Spring Boot app (e.g. localhost:8080).
          </p>
        </section>
      </main>
    </div>
  )
}
