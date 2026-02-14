'use client'

type FeedbackOverlayProps = {
  correct: boolean
  explanation: string
  onNext: () => void
}

export default function FeedbackOverlay({
  correct,
  explanation,
  onNext,
}: FeedbackOverlayProps) {
  return (
    <div
      className="mt-4 rounded-lg border p-4"
      style={{
        borderColor: correct
          ? 'var(--docs-accent)'
          : 'var(--docs-border)',
        backgroundColor: correct
          ? 'rgba(34, 197, 94, 0.08)'
          : 'rgba(239, 68, 68, 0.08)',
      }}
    >
      <p className="font-medium text-[var(--docs-text)]">
        {correct ? 'Correct' : 'Incorrect'}
      </p>
      <p className="mt-2 whitespace-pre-wrap text-sm text-[var(--docs-muted)]">
        {explanation}
      </p>
      <button
        type="button"
        onClick={onNext}
        className="mt-4 rounded-md bg-[var(--docs-accent)] px-3 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2"
      >
        Next question
      </button>
    </div>
  )
}
