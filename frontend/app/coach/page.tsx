'use client'

import AppHeader from '../components/AppHeader'
import Link from 'next/link'
import type { CoachExam, ExamCode } from '@/types/coach'
import { EXAM_CONFIG } from '@/types/coach'
import { useCallback, useEffect, useState } from 'react'

const EXAM_CODE_PATH: Record<ExamCode, string> = {
  SIE: 'SIE',
  SERIES_7: 'SERIES_7',
  SERIES_57: 'SERIES_57',
}

function licenseLabel(code: ExamCode): string {
  switch (code) {
    case 'SIE':
      return 'SIE'
    case 'SERIES_7':
      return 'Series 7'
    case 'SERIES_57':
      return 'Series 57'
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
  return s === 'SIE' || s === 'SERIES_7' || s === 'SERIES_57'
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

  const loadExams = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/coach/exams')
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
              const isSIE = exam.code === 'SIE'
              return (
                <li
                  key={exam.id}
                  className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4"
                >
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
                      href={`/coach/${path}`}
                      className="rounded-lg bg-[var(--docs-accent)] px-3 py-1.5 text-sm font-medium text-white hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                    >
                      Practice exam
                    </Link>
                    {isSIE && (
                      <Link
                        href="/chat?exam=SIE"
                        className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-1.5 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                      >
                        Tutor session
                      </Link>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </main>
    </>
  )
}
