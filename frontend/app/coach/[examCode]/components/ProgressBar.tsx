'use client'

import type { CoachUserProgress } from '@/types/coach'

type ProgressBarProps = {
  progress: CoachUserProgress | null
}

export default function ProgressBar({ progress }: ProgressBarProps) {
  if (!progress || progress.totalAsked === 0) {
    return (
      <div className="mb-4 text-sm text-[var(--docs-muted)]">
        Session stats will appear after you answer questions.
      </div>
    )
  }
  const pct = Math.round((progress.correct / progress.totalAsked) * 100)
  return (
    <div className="mb-4">
      <div className="flex justify-between text-sm text-[var(--docs-muted)]">
        <span>
          {progress.correct} / {progress.totalAsked} correct
        </span>
        <span>{pct}%</span>
      </div>
      <div
        className="mt-1 h-2 w-full overflow-hidden rounded-full bg-[var(--docs-border)]"
        role="progressbar"
        aria-valuenow={progress.correct}
        aria-valuemin={0}
        aria-valuemax={progress.totalAsked}
      >
        <div
          className="h-full rounded-full bg-[var(--docs-accent)] transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}
