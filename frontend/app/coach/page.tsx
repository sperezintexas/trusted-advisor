'use client'

import AppHeader from '../components/AppHeader'
import Link from 'next/link'
import type { CoachExam, ExamCode } from '@/types/coach'
import { EXAM_CONFIG, EXAM_BREAKDOWN } from '@/types/coach'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import { useCallback, useEffect, useState } from 'react'

const EXAM_ICONS: Record<ExamCode, JSX.Element> = {
  SIE: (
    <svg className="h-10 w-10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
      <path d="M12 18v-6" />
      <path d="M9 15h6" />
    </svg>
  ),
  SERIES_7: (
    <svg className="h-10 w-10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M3 3v18h18" />
      <path d="m19 9-5 5-4-4-3 3" />
    </svg>
  ),
  SERIES_57: (
    <svg className="h-10 w-10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M12 20V10" />
      <path d="M18 20V4" />
      <path d="M6 20v-4" />
    </svg>
  ),
  SERIES_65: (
    <svg className="h-10 w-10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M12 3v18" />
      <path d="m8 7 4-4 4 4" />
      <path d="m8 17 4 4 4-4" />
      <circle cx="12" cy="12" r="2" />
    </svg>
  ),
}

const EXAM_CODE_PATH: Record<ExamCode, string> = {
  SIE: 'SIE',
  SERIES_7: 'SERIES_7',
  SERIES_57: 'SERIES_57',
  SERIES_65: 'SERIES_65',
}

function licenseLabel(code: ExamCode): string {
  switch (code) {
    case 'SIE':
      return 'SIE'
    case 'SERIES_7':
      return 'Series 7'
    case 'SERIES_57':
      return 'Series 57'
    case 'SERIES_65':
      return 'Series 65'
    default:
      return code
  }
}

function formatTimeAllowed(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (m === 0) return `${h} hr`
  return `${h} hr ${m} min`
}

function isExamCode(s: string): s is ExamCode {
  return s === 'SIE' || s === 'SERIES_7' || s === 'SERIES_57' || s === 'SERIES_65'
}

function toCoachExam(item: unknown): CoachExam | null {
  if (typeof item !== 'object' || item === null) return null
  const o = item as Record<string, unknown>
  const id = typeof o.id === 'string' ? o.id : ''
  const code = typeof o.code === 'string' && isExamCode(o.code) ? o.code : null
  const name = typeof o.name === 'string' ? o.name : ''
  const version = typeof o.version === 'string' ? o.version : ''
  const total = typeof o.totalQuestionsInOutline === 'number' ? o.totalQuestionsInOutline : 0
  const createdAt = typeof o.createdAt === 'string' ? o.createdAt : ''
  const updatedAt = typeof o.updatedAt === 'string' ? o.updatedAt : ''
  if (!id || !code) return null
  return { id, code, name, version, totalQuestionsInOutline: total, createdAt, updatedAt }
}

export default function CoachPage() {
  const [exams, setExams] = useState<CoachExam[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [breakdownExam, setBreakdownExam] = useState<ExamCode | null>(null)

  const loadExams = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(apiUrl('/coach/exams'), defaultFetchOptions())
      if (!res.ok) throw new Error(res.statusText)
      const data = await res.json()
      const list = Array.isArray(data) ? data.map(toCoachExam).filter((e): e is CoachExam => e !== null) : []
      setExams(list)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load exams')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadExams()
  }, [loadExams])

  return (
    <>
      <AppHeader />
      <main className="mx-auto max-w-2xl px-4 py-8">
        <h1 className="mb-2 text-2xl font-semibold text-[var(--docs-text)]">Exam Coach</h1>
        <p className="mb-4 text-[var(--docs-muted)]">
          Practice for FINRA licensing exams. Choose an exam to start.
        </p>
        <p className="mb-6">
          <Link
            href="/coach/history"
            className="text-sm text-[var(--docs-accent)] hover:underline"
          >
            View exam history
          </Link>
        </p>
        {loading && <p className="text-[var(--docs-muted)]">Loading exams…</p>}
        {error && (
          <p className="text-red-600" role="alert">
            {error}
          </p>
        )}
        {!loading && !error && exams.length === 0 && (
          <p className="text-[var(--docs-muted)]">No exams available.</p>
        )}
        {!loading && exams.length > 0 && (
          <ul className="grid gap-3 sm:grid-cols-2">
            {exams.map((exam) => {
              const path = EXAM_CODE_PATH[exam.code]
              return (
                <li
                  key={exam.id}
                  className="rounded-xl border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4 shadow-sm transition-shadow hover:shadow-md"
                >
                  <div className="flex items-start gap-3">
                    <button
                      type="button"
                      onClick={() => setBreakdownExam(breakdownExam === exam.code ? null : exam.code)}
                      className="flex shrink-0 items-center justify-center rounded-lg bg-white p-2 text-[var(--docs-accent)] ring-1 ring-[var(--docs-border)] hover:bg-[var(--docs-accent)] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                      title="View exam breakdown"
                      aria-label={`View topic breakdown for ${licenseLabel(exam.code)}`}
                    >
                      {EXAM_ICONS[exam.code]}
                    </button>
                    <div className="min-w-0 flex-1">
                      <span className="text-xs font-medium uppercase tracking-wide text-[var(--docs-accent)]">
                        {licenseLabel(exam.code)}
                      </span>
                      <span className="mt-1 block font-medium text-[var(--docs-text)]">
                        {exam.name}
                      </span>
                      <span className="mt-1 block text-sm text-[var(--docs-muted)]">
                        {exam.version} · {exam.totalQuestionsInOutline} questions ·{' '}
                        {formatTimeAllowed(EXAM_CONFIG[exam.code].timeMinutes)} allowed
                      </span>
                      <div className="mt-4 flex flex-wrap gap-2">
                        <Link
                          href={`/coach/${path}?mode=practice`}
                          className="rounded-lg bg-[var(--docs-accent)] px-3 py-1.5 text-sm font-medium text-white hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                        >
                          Practice exam (20 q)
                        </Link>
                        <Link
                          href={`/coach/${path}?mode=full`}
                          className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-1.5 text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                        >
                          Full exam ({exam.totalQuestionsInOutline} q)
                        </Link>
                      </div>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        {breakdownExam && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="breakdown-title"
            onClick={() => setBreakdownExam(null)}
          >
            <div
              className="max-h-[85vh] w-full max-w-md overflow-auto rounded-xl border border-[var(--docs-border)] bg-white p-5 shadow-xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="mb-3 flex items-center justify-between">
                <h2 id="breakdown-title" className="text-lg font-semibold text-[var(--docs-text)]">
                  {licenseLabel(breakdownExam)} — Topic breakdown
                </h2>
                <button
                  type="button"
                  onClick={() => setBreakdownExam(null)}
                  className="rounded-lg p-1.5 text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)] hover:text-[var(--docs-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                  aria-label="Close"
                >
                  <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M18 6 6 18M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <p className="mb-4 text-sm text-[var(--docs-muted)]">
                FINRA-aligned weight of questions by topic. Practice exams use this distribution.
              </p>
              <ul className="space-y-3">
                {EXAM_BREAKDOWN[breakdownExam].map((item, i) => (
                  <li key={i} className="flex items-baseline justify-between gap-2 border-b border-[var(--docs-border)] pb-2 last:border-b-0 last:pb-0">
                    <span className="text-sm text-[var(--docs-text)]">{item.topic}</span>
                    <span className="shrink-0 text-sm font-medium text-[var(--docs-accent)]">{item.weightPercent}%</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </main>
    </>
  )
}
