'use client'

import AppHeader from '../components/AppHeader'
import {
  isAuthDebugEnabled,
  authDebugLog,
  checkSessionStatus,
} from '@/lib/auth'
import { apiUrl, setStoredApiKey, clearStoredApiKey, defaultFetchOptions } from '@/lib/api'
import { useEffect, useState } from 'react'

export default function LoginPage() {
  const [apiKey, setApiKey] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const debugOn = isAuthDebugEnabled()

  useEffect(() => {
    if (!debugOn) return
    authDebugLog('Login page loaded')
    checkSessionStatus().then((status) => authDebugLog('GET /me', status))
    fetch(apiUrl('/debug/auth'), defaultFetchOptions())
      .then((r) => (r.ok ? r.json() : null))
      .then((data: { apiKeyConfigured?: boolean; authDebugEnabled?: boolean } | null) => {
        if (data) {
          authDebugLog('Backend', {
            apiKeyConfigured: data.apiKeyConfigured ?? false,
            authDebugEnabled: data.authDebugEnabled ?? false,
          })
        }
      })
      .catch(() => authDebugLog('Backend /api/debug/auth', 'request failed'))
  }, [debugOn])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    const key = apiKey.trim()
    if (!key) {
      setError('Enter your password.')
      return
    }
    setSubmitting(true)
    try {
      setStoredApiKey(key)
      const res = await fetch(apiUrl('/me'), defaultFetchOptions())
      if (!res.ok) {
        clearStoredApiKey()
        setError(res.status === 401 ? 'Wrong password.' : 'Something went wrong. Try again.')
        return
      }
      window.location.href = '/chat'
    } catch {
      clearStoredApiKey()
      setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="mx-auto flex min-h-[80vh] flex-1 flex-col items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm rounded-lg border border-[var(--docs-border)] bg-white p-8 shadow-sm">
          <h1 className="text-xl font-semibold text-[var(--docs-text)]">
            Sign in
          </h1>
          <p className="mt-2 text-sm text-[var(--docs-muted)]">
            Enter your password to continue.
          </p>
          <form onSubmit={handleSubmit} className="mt-6">
            <label htmlFor="password" className="sr-only">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="Password"
              autoComplete="current-password"
              className="w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
              disabled={submitting}
            />
            {error && (
              <p className="mt-2 text-sm text-red-600" role="alert">
                {error}
              </p>
            )}
            <button
              type="submit"
              disabled={submitting}
              className="mt-4 w-full rounded-lg bg-black px-4 py-3 text-sm font-medium text-white hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2 disabled:opacity-50"
            >
              {submitting ? 'Checking…' : 'Sign in'}
            </button>
          </form>
          {debugOn && (
            <p className="mt-4 text-center text-xs text-[var(--docs-muted)]">
              Auth debug on. Open Console (F12) for [auth] logs.
            </p>
          )}
        </div>
      </main>
    </div>
  )
}
