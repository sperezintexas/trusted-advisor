'use client'

import AppHeader from '../../components/AppHeader'
import QuestionCard from './components/QuestionCard'
import AnswerButtons from './components/AnswerButtons'
import type {
  PracticeExamQuestion,
  PracticeSessionResponse,
  ScoreResponse,
  ChoiceLetter,
  ExamCode,
  CoachExamAttempt,
  CheckAnswerResult,
} from '@/types/coach'
import { EXAM_CONFIG } from '@/types/coach'
import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'next/navigation'
import { apiUrl, defaultFetchOptions } from '@/lib/api'

const VALID_EXAM_CODES: ExamCode[] = ['SIE', 'SERIES_7', 'SERIES_57', 'SERIES_65']

function isExamCode(s: string): s is ExamCode {
  return VALID_EXAM_CODES.includes(s as ExamCode)
}

function toPracticeQuestion(raw: unknown): PracticeExamQuestion | null {
  if (typeof raw !== 'object' || raw === null) return null
  const o = raw as Record<string, unknown>
  const id = typeof o.id === 'string' ? o.id : ''
  const question = typeof o.question === 'string' ? o.question : ''
  const choicesRaw = Array.isArray(o.choices) ? o.choices : []
  const choices = choicesRaw.map((c: unknown) => {
    if (typeof c !== 'object' || c === null) return null
    const cc = c as Record<string, unknown>
    const letter =
      typeof cc.letter === 'string' && ['A', 'B', 'C', 'D'].includes(cc.letter)
        ? (cc.letter as ChoiceLetter)
        : 'A'
    const text = typeof cc.text === 'string' ? cc.text : ''
    return { letter, text }
  }).filter((c): c is { letter: ChoiceLetter; text: string } => c !== null)
  if (!id || !question || choices.length === 0) return null
  return { id, question, choices }
}

function toSession(raw: unknown): PracticeSessionResponse | null {
  if (typeof raw !== 'object' || raw === null) return null
  const o = raw as Record<string, unknown>
  const questionsRaw = Array.isArray(o.questions) ? o.questions : []
  const questions = questionsRaw
    .map(toPracticeQuestion)
    .filter((q): q is PracticeExamQuestion => q !== null)
  const totalMinutes = typeof o.totalMinutes === 'number' ? o.totalMinutes : 75
  if (questions.length === 0) return null
  return { questions, totalMinutes }
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

function toAttempt(raw: unknown): CoachExamAttempt | null {
  if (typeof raw !== 'object' || raw === null) return null
  const o = raw as Record<string, unknown>
  const id = typeof o.id === 'string' ? o.id : ''
  const examCodeVal = typeof o.examCode === 'string' ? (o.examCode as ExamCode) : null
  if (!id || !examCodeVal) return null
  return {
    id,
    userId: typeof o.userId === 'string' ? o.userId : '',
    examCode: examCodeVal,
    correct: typeof o.correct === 'number' ? o.correct : 0,
    total: typeof o.total === 'number' ? o.total : 0,
    percentage: typeof o.percentage === 'number' ? o.percentage : 0,
    passed: o.passed === true,
    completedAt: typeof o.completedAt === 'string' ? o.completedAt : '',
    createdAt: typeof o.createdAt === 'string' ? o.createdAt : '',
  }
}

function formatAttemptDate(iso: string): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleDateString(undefined, {
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

function formatMinutes(min: number): string {
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  const m = min % 60
  if (m === 0) return `${h} hr`
  return `${h} hr ${m} min`
}

type Phase = 'start' | 'exam' | 'results'

const PRACTICE_QUESTION_COUNT = 20

export default function CoachExamContent() {
  const params = useParams()
  const examCodeParam = typeof params.examCode === 'string' ? params.examCode : ''
  const examCode: ExamCode | null = isExamCode(examCodeParam) ? examCodeParam : null

  const [phase, setPhase] = useState<Phase>('start')
  const [session, setSession] = useState<PracticeSessionResponse | null>(null)
  const [currentIndex, setCurrentIndex] = useState(0)
  const [answers, setAnswers] = useState<Record<string, ChoiceLetter>>({})
  const [timeRemainingSeconds, setTimeRemainingSeconds] = useState(0)
  const [score, setScore] = useState<ScoreResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [attemptHistory, setAttemptHistory] = useState<CoachExamAttempt[]>([])
  const [checkResults, setCheckResults] = useState<Record<string, CheckAnswerResult>>({})
  const [explanationVisible, setExplanationVisible] = useState<Record<string, boolean>>({})
  const [checkLoading, setCheckLoading] = useState(false)
  const [grokHintLoading, setGrokHintLoading] = useState(false)
  const [grokHints, setGrokHints] = useState<Record<string, string>>({})
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const config = examCode ? EXAM_CONFIG[examCode] : null
  const fullQuestionCount = config?.questionCount ?? 75
  const fullTimeMinutes = config?.timeMinutes ?? 105

  const currentQuestion: PracticeExamQuestion | null =
    session?.questions[currentIndex] ?? null
  const currentQuestionId = currentQuestion?.id ?? ''

  const startExam = useCallback(
    async (count: number) => {
      if (!examCode || !config) return
      setLoading(true)
      setError(null)
      try {
        const res = await fetch(
          apiUrl(`/coach/exams/${examCode}/practice-session?count=${count}`),
          defaultFetchOptions()
        )
      if (!res.ok) throw new Error(res.statusText)
      const data = await res.json()
      const sess = toSession(data)
      if (!sess) {
        setError('No questions available for this exam.')
        return
      }
      setSession(sess)
      setPhase('exam')
      setCurrentIndex(0)
      setAnswers({})
      setCheckResults({})
      setExplanationVisible({})
      const isPractice = count <= PRACTICE_QUESTION_COUNT
      const minutes = isPractice
        ? Math.round(fullTimeMinutes * (count / fullQuestionCount))
        : sess.totalMinutes
      setTimeRemainingSeconds(minutes * 60)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load exam')
    } finally {
      setLoading(false)
    }
  },
    [examCode, config, fullQuestionCount, fullTimeMinutes]
  )

  const submitExam = useCallback(
    async (saveResults: boolean) => {
      if (!examCode || !session) return
      const answersList = session.questions
        .filter((q) => answers[q.id] != null)
        .map((q) => ({ questionId: q.id, selectedLetter: answers[q.id] as ChoiceLetter }))
      const res = await fetch(apiUrl(`/coach/exams/${examCode}/score`), defaultFetchOptions({
        method: 'POST',
        body: JSON.stringify({
          answers: answersList,
          save: saveResults,
        }),
      }))
    if (!res.ok) {
      setError('Failed to score exam')
      return
    }
    const data = await res.json()
      setScore({
        correct: data.correct ?? 0,
        total: data.total ?? 0,
        percentage: data.percentage ?? 0,
        passed: data.passed ?? false,
        passingPercentage: data.passingPercentage ?? 70,
      })
      setPhase('results')
    },
    [examCode, session, answers]
  )

  // Timer countdown
  useEffect(() => {
    if (phase !== 'exam' || timeRemainingSeconds <= 0) return
    timerRef.current = setInterval(() => {
      setTimeRemainingSeconds((prev) => {
        if (prev <= 1) {
          if (timerRef.current) clearInterval(timerRef.current)
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [phase, timeRemainingSeconds])

  // Auto-submit when time hits 0 (saves results)
  useEffect(() => {
    if (phase === 'exam' && timeRemainingSeconds === 0 && session) {
      submitExam(true)
    }
  }, [phase, timeRemainingSeconds, session, submitExam])

  const fetchAttemptHistory = useCallback(async () => {
    if (!examCode) return
    try {
      const res = await fetch(
        apiUrl(`/coach/exams/${examCode}/history`),
        defaultFetchOptions()
      )
      if (!res.ok) return
      const data = await res.json()
      const list = Array.isArray(data)
        ? data.map(toAttempt).filter((a): a is CoachExamAttempt => a !== null)
        : []
      setAttemptHistory(list.slice(0, 10))
    } catch {
      // ignore
    }
  }, [examCode])

  useEffect(() => {
    if (examCode && phase === 'start') fetchAttemptHistory()
  }, [examCode, phase, fetchAttemptHistory])

  const cancelExam = useCallback(() => {
    if (
      typeof window !== 'undefined' &&
      !window.confirm(
        'Exit without saving? Your progress and score will not be recorded.'
      )
    ) {
      return
    }
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = null
    setPhase('start')
    setSession(null)
    setCurrentIndex(0)
    setAnswers({})
    setCheckResults({})
    setExplanationVisible({})
    setTimeRemainingSeconds(0)
    setError(null)
  }, [])

  const handleSelect = useCallback(
    (letter: ChoiceLetter) => {
      setAnswers((prev) => ({ ...prev, [currentQuestionId]: letter }))
    },
    [currentQuestionId]
  )

  const handleCheckAnswer = useCallback(async () => {
    if (!examCode || !currentQuestionId || answers[currentQuestionId] == null) return
    setCheckLoading(true)
    setError(null)
    try {
      const letter = answers[currentQuestionId] as ChoiceLetter
      const res = await fetch(
        apiUrl(`/coach/exams/${examCode}/check?questionId=${encodeURIComponent(currentQuestionId)}&selectedLetter=${letter}`),
        defaultFetchOptions()
      )
      if (!res.ok) throw new Error('Check failed')
      const data = (await res.json()) as CheckAnswerResult
      setCheckResults((prev) => ({ ...prev, [currentQuestionId]: data }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not check answer')
    } finally {
      setCheckLoading(false)
    }
  }, [examCode, currentQuestionId, answers])

  const handleShowExplanation = useCallback(() => {
    if (!currentQuestionId) return
    setExplanationVisible((prev) => ({ ...prev, [currentQuestionId]: true }))
  }, [currentQuestionId])

  const handleGrokHint = useCallback(async () => {
    if (!currentQuestion || grokHintLoading) return
    const qid = currentQuestion.id
    if (grokHints[qid]) return // already have a hint for this question
    setGrokHintLoading(true)
    setError(null)
    const choicesText = currentQuestion.choices
      .map((c) => `${c.letter}) ${c.text}`)
      .join('\n')
    const message = `I'm doing a practice exam question. Give me a brief hint or explanation to help me understand the concept, but do not reveal the correct answer.\n\nQuestion: ${currentQuestion.question}\n\nChoices:\n${choicesText}`
    try {
      const res = await fetch(apiUrl('/chat'), defaultFetchOptions({
        method: 'POST',
        body: JSON.stringify({ message }),
      }))
      if (!res.ok) throw new Error('Grok request failed')
      const data = (await res.json()) as { response?: string }
      const response = typeof data.response === 'string' ? data.response : 'No response.'
      setGrokHints((prev) => ({ ...prev, [qid]: response }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not get hint')
    } finally {
      setGrokHintLoading(false)
    }
  }, [currentQuestion, grokHintLoading, grokHints])

  if (!examCode) {
    return (
      <>
        <AppHeader />
        <main className="mx-auto max-w-2xl px-4 py-8">
          <p className="text-[var(--docs-muted)]">Invalid exam.</p>
          <Link
            href="/coach"
            className="mt-4 inline-block text-[var(--docs-accent)] hover:underline"
          >
            Back to Coach
          </Link>
        </main>
      </>
    )
  }

  return (
    <>
      <AppHeader />
      <main className="mx-auto max-w-2xl px-4 py-8">
        <div className="mb-4 flex items-center gap-2 text-sm text-[var(--docs-muted)]">
          <Link href="/coach" className="hover:text-[var(--docs-accent)] hover:underline">
            Coach
          </Link>
          <span>/</span>
          <span>{examCodeParam}</span>
        </div>

        {error && (
          <p className="mb-4 text-red-600" role="alert">
            {error}
          </p>
        )}

        {/* Start screen */}
        {phase === 'start' && config && (
          <>
            <div className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-6">
              <h2 className="text-xl font-semibold text-[var(--docs-text)]">
                Choose exam format
              </h2>
              <p className="mt-2 text-sm text-[var(--docs-muted)]">
                Timed, multiple choice. Score and pass/fail at the end.
              </p>

              <div className="mt-6 grid gap-4 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={() => startExam(PRACTICE_QUESTION_COUNT)}
                  disabled={loading}
                  className="rounded-lg border-2 border-[var(--docs-border)] p-4 text-left transition-colors hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)]/80 disabled:opacity-60"
                >
                  <span className="font-medium text-[var(--docs-text)]">
                    Practice (random)
                  </span>
                  <span className="mt-1 block text-sm text-[var(--docs-muted)]">
                    {PRACTICE_QUESTION_COUNT} questions ·{' '}
                    {formatMinutes(
                      Math.round(
                        fullTimeMinutes * (PRACTICE_QUESTION_COUNT / fullQuestionCount)
                      )
                    )}{' '}
                    allowed
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => startExam(fullQuestionCount)}
                  disabled={loading}
                  className="rounded-lg border-2 border-[var(--docs-border)] p-4 text-left transition-colors hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)]/80 disabled:opacity-60"
                >
                  <span className="font-medium text-[var(--docs-text)]">
                    Full exam
                  </span>
                  <span className="mt-1 block text-sm text-[var(--docs-muted)]">
                    {fullQuestionCount} questions ·{' '}
                    {formatMinutes(fullTimeMinutes)} allowed
                  </span>
                </button>
              </div>
              {loading && (
                <p className="mt-4 text-sm text-[var(--docs-muted)]">
                  Loading…
                </p>
              )}
            </div>
            {attemptHistory.length > 0 && (
              <div className="mt-6">
                <h3 className="mb-2 text-sm font-medium text-[var(--docs-muted)]">
                  Recent attempts
                </h3>
                <ul className="space-y-2">
                  {attemptHistory.map((a) => (
                    <li
                      key={a.id}
                      className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] px-4 py-2 text-sm"
                    >
                      <span
                        className={a.passed ? 'text-green-600' : 'text-red-600'}
                      >
                        {a.passed ? 'Passed' : 'Not passed'}
                      </span>
                      {' · '}
                      {a.correct}/{a.total} ({a.percentage.toFixed(1)}%) ·{' '}
                      {formatAttemptDate(a.completedAt)}
                    </li>
                  ))}
                </ul>
                <Link
                  href="/coach/history"
                  className="mt-2 inline-block text-sm text-[var(--docs-accent)] hover:underline"
                >
                  View all exam history
                </Link>
              </div>
            )}
          </>
        )}

        {/* Exam in progress */}
        {phase === 'exam' && session && (
          <>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-3">
                <span className="text-sm text-[var(--docs-muted)]">
                  Question {currentIndex + 1} of {session.questions.length}
                </span>
                <button
                  type="button"
                  onClick={cancelExam}
                  className="text-sm text-[var(--docs-muted)] underline hover:text-[var(--docs-text)]"
                >
                  Cancel exam
                </button>
              </div>
              <div
                className={`text-sm font-medium ${
                  timeRemainingSeconds <= 300 ? 'text-red-600' : 'text-[var(--docs-text)]'
                }`}
              >
                Time remaining: {formatTime(timeRemainingSeconds)}
              </div>
            </div>
            <div
              className="mb-2 h-1.5 w-full overflow-hidden rounded-full bg-[var(--docs-border)]"
              role="progressbar"
              aria-valuenow={currentIndex + 1}
              aria-valuemin={1}
              aria-valuemax={session.questions.length}
            >
              <div
                className="h-full rounded-full bg-[var(--docs-accent)] transition-all"
                style={{
                  width: `${((currentIndex + 1) / session.questions.length) * 100}%`,
                }}
              />
            </div>

            {currentQuestion && (
              <>
                <QuestionCard question={currentQuestion} />
                <AnswerButtons
                  choices={currentQuestion.choices}
                  selected={answers[currentQuestion.id] ?? null}
                  disabled={checkLoading}
                  onSelect={handleSelect}
                  checkResult={
                    checkResults[currentQuestion.id]
                      ? {
                          correct: checkResults[currentQuestion.id].correct,
                          correctLetter: checkResults[currentQuestion.id].correctLetter,
                        }
                      : null
                  }
                />
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  <button
                    type="button"
                    onClick={() => void handleCheckAnswer()}
                    disabled={
                      answers[currentQuestion.id] == null ||
                      checkResults[currentQuestion.id] != null ||
                      checkLoading
                    }
                    className="rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                  >
                    {checkLoading ? 'Checking…' : 'Check Answer'}
                  </button>
                  {checkResults[currentQuestion.id] && (
                    <button
                      type="button"
                      onClick={handleShowExplanation}
                      disabled={explanationVisible[currentQuestion.id] === true}
                      className="rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                    >
                      Show Explanation
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => void handleGrokHint()}
                    disabled={grokHintLoading || !!grokHints[currentQuestion.id]}
                    className="rounded-lg border border-[var(--docs-accent)] bg-[var(--docs-accent)]/10 px-4 py-2 text-sm font-medium text-[var(--docs-accent)] hover:bg-[var(--docs-accent)]/20 disabled:opacity-50"
                  >
                    {grokHintLoading ? 'Asking Grok…' : grokHints[currentQuestion.id] ? 'Grok hint shown' : 'Grok'}
                  </button>
                </div>
                {grokHints[currentQuestion.id] && (
                  <div
                    className="mt-4 rounded-lg border border-[var(--docs-accent)]/40 bg-[var(--docs-accent)]/5 p-4 text-sm text-[var(--docs-text)]"
                    role="region"
                    aria-label="Grok hint"
                  >
                    <p className="font-medium text-[var(--docs-accent)]">Grok hint</p>
                    <p className="mt-2 whitespace-pre-wrap">{grokHints[currentQuestion.id]}</p>
                  </div>
                )}
                {explanationVisible[currentQuestion.id] && checkResults[currentQuestion.id]?.explanation && (
                  <div
                    className="mt-4 rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4 text-sm text-[var(--docs-text)]"
                    role="region"
                    aria-label="Explanation"
                  >
                    <p className="font-medium text-[var(--docs-muted)]">Explanation</p>
                    <p className="mt-2 whitespace-pre-wrap">{checkResults[currentQuestion.id].explanation}</p>
                  </div>
                )}
              </>
            )}

            <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
              <button
                type="button"
                onClick={() => setCurrentIndex((i) => Math.max(0, i - 1))}
                disabled={currentIndex === 0}
                className="rounded-lg border border-[var(--docs-border)] px-4 py-2 text-sm text-[var(--docs-text)] disabled:opacity-50"
              >
                Previous
              </button>
              {currentIndex < session.questions.length - 1 ? (
                <button
                  type="button"
                  onClick={() => setCurrentIndex((i) => i + 1)}
                  className="rounded-lg bg-[var(--docs-accent)] px-4 py-2 text-sm text-white"
                >
                  Next
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => submitExam(true)}
                  className="rounded-lg bg-[var(--docs-accent)] px-4 py-2 text-sm font-medium text-white"
                >
                  Submit exam
                </button>
              )}
            </div>

            {/* Question nav dots for quick jump */}
            <div className="mt-4 flex flex-wrap gap-1">
              {session.questions.map((q, i) => (
                <button
                  key={q.id}
                  type="button"
                  onClick={() => setCurrentIndex(i)}
                  className={`h-8 w-8 rounded text-xs ${
                    i === currentIndex
                      ? 'bg-[var(--docs-accent)] text-white'
                      : answers[q.id]
                        ? 'bg-[var(--docs-accent)]/20 text-[var(--docs-text)]'
                        : 'border border-[var(--docs-border)] text-[var(--docs-muted)]'
                  }`}
                  title={`Question ${i + 1}`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          </>
        )}

        {/* Results */}
        {phase === 'results' && score && (
          <div className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-6">
            <h2 className="text-xl font-semibold text-[var(--docs-text)]">
              Practice exam results
            </h2>
            <p
              className={`mt-4 text-2xl font-bold ${
                score.passed ? 'text-green-600' : 'text-red-600'
              }`}
            >
              {score.passed ? 'Passed' : 'Not passed'}
            </p>
            <p className="mt-2 text-[var(--docs-text)]">
              {score.correct} of {score.total} correct (
              {score.percentage.toFixed(1)}%). Passing score is{' '}
              {score.passingPercentage}%.
            </p>
            <Link
              href="/coach"
              className="mt-6 inline-block text-[var(--docs-accent)] hover:underline"
            >
              Back to exam list
            </Link>
            <button
              type="button"
              onClick={() => {
                setPhase('start')
                setSession(null)
                setScore(null)
                setError(null)
              }}
              className="ml-4 rounded-lg border border-[var(--docs-border)] px-4 py-2 text-sm text-[var(--docs-text)]"
            >
              Take another practice exam
            </button>
          </div>
        )}
      </main>
    </>
  )
}
