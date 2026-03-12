'use client'

import AppHeader from '../components/AppHeader'
import { isAuthDebugEnabled, authDebugLog, checkSessionStatus } from '@/lib/auth'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import { useEffect } from 'react'

export default function LoginPage() {
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

  function loginWithGoogle() {
    window.location.href = `${apiUrl('/oauth2/authorization/google')}`.replace('/api/api', '/api')
  }

  function loginWithGithub() {
    window.location.href = `${apiUrl('/oauth2/authorization/github')}`.replace('/api/api', '/api')
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
            Sign in with your Google or GitHub account. Only emails pre-approved
            in the system can access Trusted Advisor.
          </p>
          <div className="mt-6 space-y-3">
            <button
              type="button"
              onClick={loginWithGoogle}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-black px-4 py-3 text-sm font-medium text-white hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2"
            >
              Continue with Google
            </button>
            <button
              type="button"
              onClick={loginWithGithub}
              className="flex w-full items-center justify-center gap-2 rounded-lg border border-[var(--docs-border)] bg-white px-4 py-3 text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2"
            >
              Continue with GitHub
            </button>
          </div>
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