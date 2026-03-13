'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import AppHeader from '../components/AppHeader'
import AdminGuard from '../components/AdminGuard'
import { useAuth } from '@/lib/auth'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import {
  fetchAccessRequests,
  approveAccessRequest,
  rejectAccessRequest,
  type AccessRequestView,
} from '@/lib/admin'

type ChatConfig = {
  debug?: boolean
  tools?: Record<string, boolean>
  context?: Record<string, string>
}

type GrokTestResult = {
  success: boolean
  message: string
}

type AuthDebug = {
  apiKeyConfigured: boolean
  authDebugEnabled: boolean
  userId: string | null
  username: string | null
}

type ConfigTab = 'system' | 'access-requests'

export default function ConfigPage() {
  const { user } = useAuth()
  const [origin, setOrigin] = useState('')
  const [config, setConfig] = useState<ChatConfig>({})
  const [configLoading, setConfigLoading] = useState(true)
  const [configError, setConfigError] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<GrokTestResult | null>(null)
  const [testLoading, setTestLoading] = useState(false)
  const [authDebug, setAuthDebug] = useState<AuthDebug | null>(null)
  const [authDebugLoading, setAuthDebugLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<ConfigTab>('system')
  const [accessRequests, setAccessRequests] = useState<AccessRequestView[]>([])
  const [accessLoading, setAccessLoading] = useState(false)
  const [accessError, setAccessError] = useState<string | null>(null)
  const [processingRequestId, setProcessingRequestId] = useState<string | null>(null)

  useEffect(() => {
    setOrigin(window.location.origin)
  }, [])

  const loadConfig = useCallback(async () => {
    setConfigLoading(true)
    setConfigError(null)
    try {
      const res = await fetch(apiUrl('/chat/config'), defaultFetchOptions())
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

  const loadAuthDebug = useCallback(async () => {
    setAuthDebugLoading(true)
    try {
      const res = await fetch(apiUrl('/debug/auth'), defaultFetchOptions())
      if (!res.ok) throw new Error(`${res.status}`)
      const data = (await res.json()) as AuthDebug
      setAuthDebug(data)
    } catch {
      setAuthDebug(null)
    } finally {
      setAuthDebugLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAuthDebug()
  }, [loadAuthDebug])

  const setDebug = useCallback(
    async (debug: boolean) => {
      const next = { ...config, debug }
      setConfig(next)
      try {
        const res = await fetch(apiUrl('/chat/config'), defaultFetchOptions({
          method: 'PUT',
          body: JSON.stringify(next),
        }))
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
      const res = await fetch(apiUrl('/chat/config/test'), defaultFetchOptions())
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

  const loadAccessRequests = useCallback(async () => {
    setAccessLoading(true)
    setAccessError(null)
    const result = await fetchAccessRequests('PENDING')
    if (result === null) {
      setAccessError('Failed to load pending access requests.')
      setAccessRequests([])
    } else {
      setAccessRequests(result.requests)
    }
    setAccessLoading(false)
  }, [])

  const handleApprove = useCallback(async (id: string) => {
    setProcessingRequestId(id)
    const result = await approveAccessRequest(id, 'Approved from config admin tab')
    setProcessingRequestId(null)
    if (!result?.success) {
      setAccessError(result?.message || 'Failed to approve request.')
      return
    }
    await loadAccessRequests()
  }, [loadAccessRequests])

  const handleReject = useCallback(async (id: string) => {
    setProcessingRequestId(id)
    const result = await rejectAccessRequest(id, 'Rejected from config admin tab')
    setProcessingRequestId(null)
    if (!result?.success) {
      setAccessError(result?.message || 'Failed to reject request.')
      return
    }
    await loadAccessRequests()
  }, [loadAccessRequests])

  useEffect(() => {
    if (activeTab !== 'access-requests') return
    void loadAccessRequests()
  }, [activeTab, loadAccessRequests])

  const formatDate = (dateStr: string): string => {
    const parsed = new Date(dateStr)
    if (Number.isNaN(parsed.getTime())) return dateStr
    return parsed.toLocaleString()
  }

  return (
    <AdminGuard>
      <div
        className="flex min-h-screen flex-col bg-white"
        style={{ backgroundImage: 'none' }}
      >
        <AppHeader />
        <main className="mx-auto w-full max-w-3xl px-4 py-10">
          <h1 className="mb-2 text-2xl font-semibold text-[var(--docs-text)]">
            Configuration
          </h1>
          <p className="mb-6 text-sm text-[var(--docs-muted)]">
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

          <div className="mb-8 flex items-center gap-2 border-b border-[var(--docs-border)]">
            <button
              type="button"
              onClick={() => setActiveTab('system')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'system'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              System
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('access-requests')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'access-requests'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              Access Requests
            </button>
          </div>

          {activeTab === 'access-requests' && (
            <section className="mb-12">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-medium text-[var(--docs-text)]">
                    Pending access requests
                  </h2>
                  <p className="text-sm text-[var(--docs-muted)]">
                    Review and approve/reject user access from this admin tab.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void loadAccessRequests()}
                  disabled={accessLoading}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {accessLoading ? 'Refreshing…' : 'Refresh'}
                </button>
              </div>
              <div className="docs-path mb-3 inline-block">
                GET /api/admin/access-requests?status=PENDING · POST /api/admin/access-requests/:id/approve
              </div>
              {accessError && (
                <p className="mb-2 text-sm text-red-600" role="alert">
                  {accessError}
                </p>
              )}
              <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                {accessLoading ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">Loading pending requests…</div>
                ) : accessRequests.length === 0 ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">No pending access requests.</div>
                ) : (
                  <table className="min-w-full divide-y divide-[var(--docs-border)]">
                    <thead className="bg-[var(--docs-code-bg)]">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">User</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Requested</th>
                        <th className="px-4 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--docs-border)]">
                      {accessRequests.map((request) => (
                        <tr key={request.id}>
                          <td className="px-4 py-3 text-sm">
                            <p className="font-medium text-[var(--docs-text)]">
                              {request.displayName || request.email}
                            </p>
                            <p className="text-xs text-[var(--docs-muted)]">{request.email}</p>
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {formatDate(request.createdAt)}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => void handleApprove(request.id)}
                                disabled={processingRequestId === request.id}
                                className="rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                onClick={() => void handleReject(request.id)}
                                disabled={processingRequestId === request.id}
                                className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
                              >
                                Reject
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
          )}

          {activeTab === 'system' && (
            <>
              <section className="mb-12">
                <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
                  Auth
                </h2>
                <p className="mb-3 text-sm text-[var(--docs-muted)]">
                  Session and API key configuration (for debugging).
                </p>
                <div className="docs-path mb-3 inline-block">
                  GET /api/debug/auth · GET /api/me
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-4">
                  {authDebugLoading && (
                    <p className="text-sm text-[var(--docs-muted)]">Loading…</p>
                  )}
                  {!authDebugLoading && authDebug && (
                    <dl className="space-y-2 text-sm">
                      <div className="flex items-center gap-2">
                        <dt className="font-medium text-[var(--docs-muted)]">
                          API key configured:
                        </dt>
                        <dd>
                          <span
                            className={
                              authDebug.apiKeyConfigured
                                ? 'text-green-600'
                                : 'text-amber-600'
                            }
                          >
                            {authDebug.apiKeyConfigured ? 'Yes' : 'No'}
                          </span>
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="font-medium text-[var(--docs-muted)]">
                          Login debug (AUTH_DEBUG):
                        </dt>
                        <dd>
                          <span
                            className={
                              authDebug.authDebugEnabled
                                ? 'text-green-600'
                                : 'text-[var(--docs-muted)]'
                            }
                          >
                            {authDebug.authDebugEnabled ? 'On' : 'Off'}
                          </span>
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="font-medium text-[var(--docs-muted)]">
                          Backend session:
                        </dt>
                        <dd className="font-mono text-xs">
                          {authDebug.userId ?? '—'}
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="font-medium text-[var(--docs-muted)]">
                          Username:
                        </dt>
                        <dd>{authDebug.username ?? '—'}</dd>
                      </div>
                      {user && (
                        <div className="flex items-center gap-2 border-t border-[var(--docs-border)] pt-2">
                          <dt className="font-medium text-[var(--docs-muted)]">
                            Frontend (useAuth):
                          </dt>
                          <dd className="flex items-center gap-2">
                            {user.profileImageUrl && (
                              <img
                                src={user.profileImageUrl}
                                alt=""
                                className="h-8 w-8 rounded-full object-cover"
                                width={32}
                                height={32}
                              />
                            )}
                            <span>
                              {user.displayName || user.username} ({user.id})
                            </span>
                          </dd>
                        </div>
                      )}
                    </dl>
                  )}
                </div>
              </section>

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
            </>
          )}
        </main>
      </div>
    </AdminGuard>
  )
}
