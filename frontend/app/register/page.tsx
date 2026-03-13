'use client'

import { useEffect, useState } from 'react'
import AppHeader from '../components/AppHeader'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import { fetchAuthSession, type AuthSession } from '@/lib/auth'

type Tier = 'BASIC' | 'PREMIUM'

export default function RegisterPage() {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [tier, setTier] = useState<Tier>('BASIC')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void fetchAuthSession().then((s) => {
      setSession(s)
      const u = s?.user
      if (u) {
        setUsername((prev) => prev || u.username)
        setDisplayName((prev) => prev || u.displayName || u.username)
      }
    })
  }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!session?.allowed) return
    setSubmitting(true)
    setError(null)
    try {
      const res = await fetch(
        apiUrl('/auth/register'),
        defaultFetchOptions({
          method: 'POST',
          body: JSON.stringify({ username, displayName, tier }),
        }),
      )
      if (!res.ok) {
        setError('Could not complete registration. Try again.')
        return
      }
      window.location.href = '/coach'
    } catch {
      setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const email = session?.user?.email ?? ''

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="mx-auto flex min-h-[80vh] flex-1 flex-col items-center justify-center px-4 py-12">
        <div className="w-full max-w-md rounded-lg border border-[var(--docs-border)] bg-white p-8 shadow-sm">
          <h1 className="text-xl font-semibold text-[var(--docs-text)]">
            Complete your profile
          </h1>
          <p className="mt-2 text-sm text-[var(--docs-muted)]">
            You&apos;ve been authorized to use Trusted Advisor. Confirm your details
            and choose your plan.
          </p>
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">
                Email
              </label>
              <div className="mt-1 rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] px-3 py-2 text-sm text-[var(--docs-text)]">
                {email || 'Loading…'}
              </div>
            </div>
            <div>
              <label
                htmlFor="username"
                className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]"
              >
                Username
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
                disabled={submitting}
              />
            </div>
            <div>
              <label
                htmlFor="displayName"
                className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]"
              >
                Display name
              </label>
              <input
                id="displayName"
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
                disabled={submitting}
              />
            </div>

            <div>
              <label className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)] mb-3">
                Choose your plan
              </label>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setTier('BASIC')}
                  className={`rounded-lg border-2 p-4 text-left transition-all ${
                    tier === 'BASIC'
                      ? 'border-[var(--docs-accent)] bg-blue-50'
                      : 'border-[var(--docs-border)] hover:border-gray-300'
                  }`}
                >
                  <div className="text-sm font-semibold text-[var(--docs-text)]">
                    Basic
                  </div>
                  <div className="text-xs text-[var(--docs-muted)] mt-1">Free</div>
                  <ul className="mt-2 text-xs text-[var(--docs-muted)] space-y-1">
                    <li>• Exam Coach access</li>
                    <li>• Practice exams</li>
                    <li>• Basic chat</li>
                  </ul>
                </button>
                <button
                  type="button"
                  onClick={() => setTier('PREMIUM')}
                  className={`rounded-lg border-2 p-4 text-left transition-all ${
                    tier === 'PREMIUM'
                      ? 'border-[var(--docs-accent)] bg-blue-50'
                      : 'border-[var(--docs-border)] hover:border-gray-300'
                  }`}
                >
                  <div className="text-sm font-semibold text-[var(--docs-text)]">
                    Premium
                  </div>
                  <div className="text-xs text-[var(--docs-muted)] mt-1">$9.99/mo</div>
                  <ul className="mt-2 text-xs text-[var(--docs-muted)] space-y-1">
                    <li>• Everything in Basic</li>
                    <li>• AI Tutor sessions</li>
                    <li>• Priority support</li>
                  </ul>
                </button>
              </div>
            </div>

            {error && (
              <p className="text-sm text-red-600" role="alert">
                {error}
              </p>
            )}
            <button
              type="submit"
              disabled={submitting || !session?.allowed}
              className="mt-2 w-full rounded-lg bg-black px-4 py-3 text-sm font-medium text-white hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2 disabled:opacity-50"
            >
              {submitting ? 'Saving…' : tier === 'PREMIUM' ? 'Continue to payment' : 'Start for free'}
            </button>
          </form>
        </div>
      </main>
    </div>
  )
}

