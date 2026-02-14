'use client'

type QuestionCardProps = {
  question: { question: string; topic?: string }
}

export default function QuestionCard({ question }: QuestionCardProps) {
  return (
    <div className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4 sm:p-6">
      <p className="whitespace-pre-wrap text-[var(--docs-text)]">{question.question}</p>
      {question.topic && (
        <p className="mt-2 text-sm text-[var(--docs-muted)]">Topic: {question.topic}</p>
      )}
    </div>
  )
}
