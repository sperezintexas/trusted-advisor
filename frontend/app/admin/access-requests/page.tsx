'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import AppHeader from '../../components/AppHeader'
import { useAuth, fetchAuthSession } from '@/lib/auth'
import {
  fetchAccessRequests,
  approveAccessRequest,
  rejectAccessRequest,
  type AccessRequestView,
} from '@/lib/admin'

type FilterStatus = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'

export default function AdminAccessRequestsPage() {
  const router = useRouter()
  const { user, loading: authLoading } = useAuth()
  const [requests, setRequests] = useState<AccessRequestView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<FilterStatus>('PENDING')
  const [processing, setProcessing] = useState<string | null>(null)
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null)

  const loadRequests = useCallback(async () => {
    setLoading(true)
    setError(null)
    const status = filter === 'ALL' ? undefined : filter
    const result = await fetchAccessRequests(status)
    if (result === null) {
      setError('Failed to load access requests. You may not have admin access.')
      setIsAdmin(false)
    } else {
      setRequests(result.requests)
      setIsAdmin(true)
    }
    setLoading(false)
  }, [filter])

  useEffect(() => {
    if (authLoading) return
    if (!user) {
      router.replace('/login')
      return
    }
    loadRequests()
  }, [authLoading, user, router, loadRequests])

  const handleApprove = async (id: string) => {
    setProcessing(id)
    const result = await approveAccessRequest(id)
    setProcessing(null)
    if (result?.success) {
      await loadRequests()
    } else {
      setError(result?.message || 'Failed to approve request')
    }
  }

  const handleReject = async (id: string) => {
    setProcessing(id)
    const result = await rejectAccessRequest(id)
    setProcessing(null)
    if (result?.success) {
      await loadRequests()
    } else {
      setError(result?.message || 'Failed to reject request')
    }
  }

  const formatDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr)
      return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return dateStr
    }
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING':
        return (
          <span className="inline-flex items-center rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-800">
            Pending
          </span>
        )
      case 'APPROVED':
        return (
          <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
            Approved
          </span>
        )
      case 'REJECTED':
        return (
          <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-800">
            Rejected
          </span>
        )
      default:
        return null
    }
  }

  if (authLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
        <p className="text-sm text-[var(--docs-muted)]">Loading...</p>
      </div>
    )
  }

  if (isAdmin === false) {
    return (
      <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
        <AppHeader />
        <main className="mx-auto flex min-h-[80vh] flex-1 flex-col items-center justify-center px-4 py-12">
          <div className="w-full max-w-md rounded-lg border border-[var(--docs-border)] bg-white p-8 shadow-sm text-center">
            <div className="mb-4 flex h-12 w-12 mx-auto items-center justify-center rounded-full bg-red-100">
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
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
            </div>
            <h1 className="text-xl font-semibold text-[var(--docs-text)]">
              Access Denied
            </h1>
            <p className="mt-2 text-sm text-[var(--docs-muted)]">
              You don&apos;t have permission to access the admin panel.
            </p>
            <button
              onClick={() => router.push('/')}
              className="mt-6 flex w-full items-center justify-center gap-2 rounded-lg border border-[var(--docs-border)] bg-white px-4 py-3 text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
            >
              Go to Home
            </button>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-[var(--docs-text)]">
            Access Requests
          </h1>
          <p className="mt-1 text-sm text-[var(--docs-muted)]">
            Review and manage user access requests
          </p>
        </div>

        {error && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4">
            <p className="text-sm text-red-600">{error}</p>
          </div>
        )}

        <div className="mb-6 flex items-center gap-2">
          <span className="text-sm text-[var(--docs-muted)]">Filter:</span>
          {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as FilterStatus[]).map(
            (status) => (
              <button
                key={status}
                onClick={() => setFilter(status)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  filter === status
                    ? 'bg-black text-white'
                    : 'bg-white text-[var(--docs-text)] border border-[var(--docs-border)] hover:bg-[var(--docs-code-bg)]'
                }`}
              >
                {status.charAt(0) + status.slice(1).toLowerCase()}
              </button>
            )
          )}
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-12">
            <p className="text-sm text-[var(--docs-muted)]">Loading requests...</p>
          </div>
        ) : requests.length === 0 ? (
          <div className="rounded-lg border border-[var(--docs-border)] bg-white p-8 text-center">
            <p className="text-sm text-[var(--docs-muted)]">
              No {filter === 'ALL' ? '' : filter.toLowerCase()} access requests found.
            </p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
            <table className="min-w-full divide-y divide-[var(--docs-border)]">
              <thead className="bg-[var(--docs-code-bg)]">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-[var(--docs-muted)]">
                    User
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-[var(--docs-muted)]">
                    Reason
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-[var(--docs-muted)]">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-[var(--docs-muted)]">
                    Requested
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-[var(--docs-muted)]">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--docs-border)]">
                {requests.map((request) => (
                  <tr key={request.id}>
                    <td className="whitespace-nowrap px-6 py-4">
                      <div className="flex items-center gap-3">
                        {request.profileImageUrl ? (
                          <img
                            src={request.profileImageUrl}
                            alt=""
                            className="h-8 w-8 rounded-full"
                          />
                        ) : (
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gray-200 text-sm font-medium text-gray-600">
                            {request.email.charAt(0).toUpperCase()}
                          </div>
                        )}
                        <div>
                          <div className="text-sm font-medium text-[var(--docs-text)]">
                            {request.displayName || request.email.split('@')[0]}
                          </div>
                          <div className="text-xs text-[var(--docs-muted)]">
                            {request.email}
                          </div>
                          {request.oauthProvider && (
                            <div className="text-xs text-[var(--docs-muted)]">
                              via {request.oauthProvider}
                            </div>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <p className="max-w-xs truncate text-sm text-[var(--docs-text)]">
                        {request.reason || '-'}
                      </p>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4">
                      {getStatusBadge(request.status)}
                      {request.reviewedBy && (
                        <p className="mt-1 text-xs text-[var(--docs-muted)]">
                          by {request.reviewedBy.split('@')[0]}
                        </p>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm text-[var(--docs-muted)]">
                      {formatDate(request.createdAt)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-right">
                      {request.status === 'PENDING' ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handleApprove(request.id)}
                            disabled={processing === request.id}
                            className="rounded-lg bg-green-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
                          >
                            {processing === request.id ? '...' : 'Approve'}
                          </button>
                          <button
                            onClick={() => handleReject(request.id)}
                            disabled={processing === request.id}
                            className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                          >
                            {processing === request.id ? '...' : 'Reject'}
                          </button>
                        </div>
                      ) : (
                        <span className="text-sm text-[var(--docs-muted)]">
                          {request.reviewedAt ? formatDate(request.reviewedAt) : '-'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  )
}
