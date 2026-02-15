'use client'

import AppHeader from '../../components/AppHeader'
import Link from 'next/link'
import type { CoachExamAttempt, ExamCode } from '@/types/coach'
import { getExamName } from '@/types/coach'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import { useCallback, useEffect, useState } from 'react'

function toAttempt(raw: unknown): CoachExamAttempt | null {
  if (typeof raw !== 'object' || raw === null) return null
  const o = raw as Record<string, unknown>
  const id = typeof o.id === 'string' ? o.id : ''
  const examCode = typeof o.examCode === 'string' ? (o.examCode as ExamCode) : null
  const correct = typeof o.correct === 'number' ? o.correct : 0
  const total = typeof o.total === 'number' ? o.total : 0
  const percentage = typeof o.percentage === 'number' ? o.percentage : 0
  const passed = o.passed === true
  const completedAt = typeof o.completedAt === 'string' ? o.completedAt : ''
  if (!id || !examCode) return null
  return {
    id,
    userId: typeof o.userId === 'string' ? o.userId : '',
    examCode,
    correct,
    total,
    percentage,
    passed,
    completedAt,
    createdAt: typeof o.createdAt === 'string' ? o.createdAt : '',
  }
}

function formatDate(iso: string): string {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    return d.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

export default function CoachHistoryPage() {
  const [attempts, setAttempts] = useState<CoachExamAttempt[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadHistory = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(
        apiUrl('/coach/history'),
        defaultFetchOptions()
      )
      if (!res.ok) throw new Error(res.statusText)
      const data = await res.json()
      const list = Array.isArray(data)
        ? data.map(toAttempt).filter((a): a is CoachExamAttempt => a !== null)
        : []
      setAttempts(list)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  return (
    <>
      <AppHeader />
      <main className="mx-auto max-w-2xl px-4 py-8">
        <div className="mb-4 flex items-center gap-2 text-sm text-[var(--docs-muted)]">
          <Link href="/coach" className="hover:text-[var(--docs-accent)] hover:underline">
            Coach
          </Link>
          <span>/</span>
          <span>Exam history</span>
        </div>

        <h1 className="mb-2 text-2xl font-semibold text-[var(--docs-text)]">
          Exam history
        </h1>
        <p className="mb-6 text-[var(--docs-muted)]">
          Past practice exam attempts. Only completed (submitted) exams are saved.
        </p>

        {loading && <p className="text-[var(--docs-muted)]">Loading…</p>}
        {error && (
          <p className="text-red-600" role="alert">
            {error}
          </p>
        )}

        {!loading && !error && attempts.length === 0 && (
          <p className="text-[var(--docs-muted)]">No exam history yet.</p>
        )}

        {!loading && attempts.length > 0 && (
          <ul className="space-y-3">
            {attempts.map((a) => (
              <li
                key={a.id}
                className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-[var(--docs-text)]">
                    {getExamName(a.examCode)}
                  </span>
                  <span
                    className={`text-sm font-medium ${
                      a.passed ? 'text-green-600' : 'text-red-600'
                    }`}
                  >
                    {a.passed ? 'Passed' : 'Not passed'}
                  </span>
                </div>
                <p className="mt-1 text-sm text-[var(--docs-muted)]">
                  {a.correct} of {a.total} correct ({a.percentage.toFixed(1)}%) ·{' '}
                  {formatDate(a.completedAt)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  )
}
