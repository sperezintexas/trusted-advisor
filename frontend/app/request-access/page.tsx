'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import AppHeader from '../components/AppHeader'
import {
  fetchAuthSession,
  submitAccessRequest,
  logout,
  type AuthSession,
} from '@/lib/auth'

export default function RequestAccessPage() {
  const router = useRouter()
  const [session, setSession] = useState<AuthSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    fetchAuthSession().then((data) => {
      setSession(data)
      setLoading(false)
      if (data?.allowed) {
        router.replace('/')
      }
    })
  }, [router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)

    const result = await submitAccessRequest(
      displayName || undefined,
      reason || undefined
    )

    setSubmitting(false)

    if (!result) {
      setError('Failed to submit request. Please try again.')
      return
    }

    if (result.success) {
      setSuccess(true)
    } else {
      if (result.status === 'PENDING') {
        setSuccess(true)
      } else {
        setError(result.message)
      }
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
        <p className="text-sm text-[var(--docs-muted)]">Loading...</p>
      </div>
    )
  }

  const requestStatus = session?.accessRequestStatus

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="mx-auto flex min-h-[80vh] flex-1 flex-col items-center justify-center px-4 py-12">
        <div className="w-full max-w-md rounded-lg border border-[var(--docs-border)] bg-white p-8 shadow-sm">
          {success || requestStatus === 'PENDING' ? (
            <>
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-blue-100">
                <svg
                  className="h-6 w-6 text-blue-600"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
              <h1 className="text-xl font-semibold text-[var(--docs-text)]">
                Access Request Pending
              </h1>
              <p className="mt-2 text-sm text-[var(--docs-muted)]">
                Your request to access Trusted Advisor has been submitted and is
                awaiting admin approval. You&apos;ll be notified once your request is
                reviewed.
              </p>
              {session?.user?.email && (
                <p className="mt-4 text-sm text-[var(--docs-muted)]">
                  Signed in as{' '}
                  <span className="font-medium">{session.user.email}</span>
                </p>
              )}
              <div className="mt-6 space-y-3">
                <button
                  type="button"
                  onClick={() => window.location.reload()}
                  className="flex w-full items-center justify-center gap-2 rounded-lg border border-[var(--docs-border)] bg-white px-4 py-3 text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                >
                  Check Status
                </button>
                <button
                  type="button"
                  onClick={logout}
                  className="flex w-full items-center justify-center gap-2 rounded-lg px-4 py-3 text-sm font-medium text-[var(--docs-muted)] hover:text-[var(--docs-text)]"
                >
                  Sign out
                </button>
              </div>
            </>
          ) : requestStatus === 'REJECTED' ? (
            <>
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
                <svg
                  className="h-6 w-6 text-red-600"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </div>
              <h1 className="text-xl font-semibold text-[var(--docs-text)]">
                Access Request Denied
              </h1>
              <p className="mt-2 text-sm text-[var(--docs-muted)]">
                Your request to access Trusted Advisor was not approved. If you
                believe this was in error, please contact the administrator.
              </p>
              {session?.user?.email && (
                <p className="mt-4 text-sm text-[var(--docs-muted)]">
                  Signed in as{' '}
                  <span className="font-medium">{session.user.email}</span>
                </p>
              )}
              <div className="mt-6">
                <button
                  type="button"
                  onClick={logout}
                  className="flex w-full items-center justify-center gap-2 rounded-lg border border-[var(--docs-border)] bg-white px-4 py-3 text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                >
                  Sign out
                </button>
              </div>
            </>
          ) : (
            <>
              <h1 className="text-xl font-semibold text-[var(--docs-text)]">
                Request Access
              </h1>
              <p className="mt-2 text-sm text-[var(--docs-muted)]">
                You don&apos;t have access to Trusted Advisor yet. Submit a request
                and an administrator will review it.
              </p>
              {session?.user?.email && (
                <p className="mt-4 text-sm text-[var(--docs-muted)]">
                  Requesting access for{' '}
                  <span className="font-medium">{session.user.email}</span>
                </p>
              )}
              <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                <div>
                  <label
                    htmlFor="displayName"
                    className="block text-sm font-medium text-[var(--docs-text)]"
                  >
                    Display Name (optional)
                  </label>
                  <input
                    type="text"
                    id="displayName"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="Your name"
                    className="mt-1 block w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
                  />
                </div>
                <div>
                  <label
                    htmlFor="reason"
                    className="block text-sm font-medium text-[var(--docs-text)]"
                  >
                    Reason for access (optional)
                  </label>
                  <textarea
                    id="reason"
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    placeholder="Why do you need access?"
                    rows={3}
                    className="mt-1 block w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
                  />
                </div>
                {error && (
                  <p className="text-sm text-red-600">{error}</p>
                )}
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-black px-4 py-3 text-sm font-medium text-white hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2 disabled:opacity-50"
                >
                  {submitting ? 'Submitting...' : 'Request Access'}
                </button>
              </form>
              <div className="mt-4 text-center">
                <button
                  type="button"
                  onClick={logout}
                  className="text-sm text-[var(--docs-muted)] hover:text-[var(--docs-text)]"
                >
                  Sign out and use a different account
                </button>
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  )
}
