export type ExamCode = 'SIE' | 'SERIES_7' | 'SERIES_57'

export type ChoiceLetter = 'A' | 'B' | 'C' | 'D'

export type Difficulty = 'easy' | 'medium' | 'hard'

export type CoachExam = {
  id: string
  code: ExamCode
  name: string
  version: string
  totalQuestionsInOutline: number
  createdAt: string
  updatedAt: string
}

export type CoachChoice = {
  letter: ChoiceLetter
  text: string
}

export type CoachQuestion = {
  id: string
  examCode: ExamCode
  question: string
  choices: CoachChoice[]
  correctLetter: ChoiceLetter
  explanation: string
  outlineReference?: string
  topic?: string
  difficulty?: Difficulty
  source?: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export type WeakTopic = {
  topic: string
  missCount: number
}

export type CoachUserProgress = {
  id: string
  userId: string
  examCode: ExamCode
  totalAsked: number
  correct: number
  lastSessionAt: string
  weakTopics: WeakTopic[]
  createdAt: string
  updatedAt: string
}

export type RecordAnswerResponse = {
  correct: boolean
}

export type CheckAnswerResult = {
  correct: boolean
  correctLetter: ChoiceLetter
  explanation: string
}

/** Practice exam question (no correct answer or explanation sent to client). */
export type PracticeExamQuestion = {
  id: string
  question: string
  choices: CoachChoice[]
}

export type PracticeSessionResponse = {
  questions: PracticeExamQuestion[]
  totalMinutes: number
}

export type ScoreAnswerRequest = {
  questionId: string
  selectedLetter: string
}

export type ScoreRequest = {
  answers: ScoreAnswerRequest[]
  userId?: string
  save?: boolean
}

export type ScoreResponse = {
  correct: number
  total: number
  percentage: number
  passed: boolean
  passingPercentage: number
}

export type CoachExamAttempt = {
  id: string
  userId: string
  examCode: ExamCode
  correct: number
  total: number
  percentage: number
  passed: boolean
  completedAt: string
  createdAt: string
}

const EXAM_NAMES: Record<ExamCode, string> = {
  SIE: 'Securities Industry Essentials',
  SERIES_7: 'General Securities Representative',
  SERIES_57: 'Securities Trader',
}

export function getExamName(code: ExamCode): string {
  return EXAM_NAMES[code] ?? code
}

/** Question count and time limit per exam (matches FINRA-style). */
export const EXAM_CONFIG: Record<
  ExamCode,
  { questionCount: number; timeMinutes: number }
> = {
  SIE: { questionCount: 75, timeMinutes: 105 },
  SERIES_7: { questionCount: 125, timeMinutes: 225 },
  SERIES_57: { questionCount: 50, timeMinutes: 90 },
}
