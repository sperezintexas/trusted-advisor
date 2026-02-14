'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import AppHeader from '../components/AppHeader'

type ChatConfig = {
  debug?: boolean
  tools?: Record<string, boolean>
  context?: Record<string, string>
}

type GrokTestResult = {
  success: boolean
  message: string
}

export default function ConfigPage() {
  const [origin, setOrigin] = useState('')
  const [config, setConfig] = useState<ChatConfig>({})
  const [configLoading, setConfigLoading] = useState(true)
  const [configError, setConfigError] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<GrokTestResult | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  useEffect(() => {
    setOrigin(window.location.origin)
  }, [])

  const loadConfig = useCallback(async () => {
    setConfigLoading(true)
    setConfigError(null)
    try {
      const res = await fetch('/api/chat/config')
      if (!res.ok) throw new Error(`Config: ${res.status}`)
      const data = (await res.json()) as ChatConfig
      setConfig(data)
    } catch (e) {
      setConfigError(e instanceof Error ? e.message : 'Failed to load config')
    } finally {
      setConfigLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadConfig()
  }, [loadConfig])

  const setDebug = useCallback(
    async (debug: boolean) => {
      const next = { ...config, debug }
      setConfig(next)
      try {
        const res = await fetch('/api/chat/config', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(next),
        })
        if (!res.ok) throw new Error(`Save: ${res.status}`)
      } catch (e) {
        setConfigError(e instanceof Error ? e.message : 'Failed to save config')
      }
    },
    [config]
  )

  const testConnection = useCallback(async () => {
    setTestLoading(true)
    setTestResult(null)
    try {
      const res = await fetch('/api/chat/config/test')
      const data = (await res.json()) as GrokTestResult
      setTestResult(data)
    } catch (e) {
      setTestResult({
        success: false,
        message: e instanceof Error ? e.message : 'Request failed',
      })
    } finally {
      setTestLoading(false)
    }
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

        {/* Debug & xAI */}
        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Debug & xAI
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            Enable debug and verify the Grok (xAI) API connection.
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/chat/config · PUT /api/chat/config · GET /api/chat/config/test
          </div>
          {configError && (
            <p className="mb-2 text-sm text-red-600" role="alert">
              {configError}
            </p>
          )}
          <div className="space-y-4 rounded-lg border border-[var(--docs-border)] bg-white p-4">
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="checkbox"
                checked={config.debug === true}
                onChange={(e) => void setDebug(e.target.checked)}
                disabled={configLoading}
                className="h-4 w-4 rounded border-[var(--docs-border)] text-[var(--docs-accent)] focus:ring-[var(--docs-accent)]"
              />
              <span className="text-sm font-medium text-[var(--docs-text)]">
                Enable debug
              </span>
            </label>
            <div>
              <button
                type="button"
                onClick={() => void testConnection()}
                disabled={testLoading}
                className="inline-flex items-center rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
              >
                {testLoading ? 'Testing…' : 'Test xAI connection'}
              </button>
              {testResult && (
                <div
                  className={`mt-2 rounded p-2 text-sm ${
                    testResult.success
                      ? 'bg-green-50 text-green-800'
                      : 'bg-red-50 text-red-800'
                  }`}
                  role="status"
                >
                  {testResult.success ? '✓ ' : '✗ '}
                  {testResult.message}
                </div>
              )}
            </div>
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
