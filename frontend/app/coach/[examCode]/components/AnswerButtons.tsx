'use client'

import type { CoachChoice, ChoiceLetter } from '@/types/coach'

type CheckResult = {
  correct: boolean
  correctLetter: ChoiceLetter
}

type AnswerButtonsProps = {
  choices: CoachChoice[]
  selected: ChoiceLetter | null
  disabled: boolean
  onSelect: (letter: ChoiceLetter) => void
  checkResult?: CheckResult | null
}

function choiceStyle(
  c: CoachChoice,
  selected: ChoiceLetter | null,
  checkResult: CheckResult | null | undefined
): string {
  const base =
    'relative w-full rounded-lg border-2 px-4 py-3 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2 disabled:opacity-60 '
  if (!checkResult) {
    const selectedStyle =
      selected === c.letter
        ? 'border-[var(--docs-accent)] bg-[var(--docs-accent)]/15 shadow-[inset_0_0_0_1px_var(--docs-accent)] dark:bg-[var(--docs-accent)]/20'
        : 'border-[var(--docs-border)] bg-white text-[var(--docs-text)] hover:border-[var(--docs-muted)] dark:bg-[var(--docs-code-bg)]'
    return base + selectedStyle
  }
  const isCorrectChoice = c.letter === checkResult.correctLetter
  const isUserChoice = c.letter === selected
  if (isCorrectChoice) {
    return base + 'border-green-600 bg-green-50 text-green-900 dark:bg-green-950/40 dark:border-green-500 dark:text-green-100'
  }
  if (isUserChoice && !checkResult.correct) {
    return base + 'border-amber-500 bg-amber-50 text-amber-900 dark:bg-amber-950/40 dark:border-amber-500 dark:text-amber-100'
  }
  return base + 'border-[var(--docs-border)] bg-white text-[var(--docs-text)] dark:bg-[var(--docs-code-bg)]'
}

export default function AnswerButtons({
  choices,
  selected,
  disabled,
  onSelect,
  checkResult = null,
}: AnswerButtonsProps) {
  const locked = checkResult != null
  return (
    <ul className="mt-4 grid gap-2 sm:grid-cols-2">
      {choices.map((c) => (
        <li key={c.letter}>
          <button
            type="button"
            disabled={disabled || locked}
            onClick={() => onSelect(c.letter)}
            className={choiceStyle(c, selected, checkResult)}
          >
            {checkResult && c.letter === checkResult.correctLetter && (
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-600 dark:text-green-400" aria-hidden>
                ✓
              </span>
            )}
            {checkResult && selected === c.letter && !checkResult.correct && (
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-amber-600 dark:text-amber-400" aria-hidden>
                ✗
              </span>
            )}
            {!checkResult && selected === c.letter && (
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--docs-accent)]" aria-hidden>
                ✓
              </span>
            )}
            <span className="font-medium">{c.letter}.</span> {c.text}
          </button>
        </li>
      ))}
    </ul>
  )
}
