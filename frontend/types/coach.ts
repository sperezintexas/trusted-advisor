export type ExamCode = 'SIE' | 'SERIES_7' | 'SERIES_57' | 'SERIES_65'

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
  recommendation?: LearningPlanRecommendation
  recommendationStatus?: RecommendationStatus
  recommendationJobSubmitted?: boolean
}

export type LearningPlanRecommendation = {
  summary: string
  suggestedTopics: string[]
  proposedLearningPlan: string[]
}

export type RecommendationStatus =
  | 'NONE'
  | 'QUEUED'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED'

export type CoachExamAttempt = {
  id: string
  userId: string
  examCode: ExamCode
  correct: number
  total: number
  percentage: number
  passed: boolean
  recommendationStatus?: RecommendationStatus
  recommendation?: LearningPlanRecommendation
  completedAt: string
  createdAt: string
}

const EXAM_NAMES: Record<ExamCode, string> = {
  SIE: 'Securities Industry Essentials',
  SERIES_7: 'General Securities Representative',
  SERIES_57: 'Securities Trader',
  SERIES_65: 'Uniform Investment Adviser Law',
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
  SERIES_65: { questionCount: 130, timeMinutes: 180 },
}

/** FINRA-aligned topic breakdown (% of questions by topic) for exam summary. */
export type ExamTopicBreakdown = { topic: string; weightPercent: number }

export const EXAM_BREAKDOWN: Record<ExamCode, ExamTopicBreakdown[]> = {
  SIE: [
    { topic: 'Knowledge of Capital Markets', weightPercent: 16 },
    { topic: 'Understanding Products and Their Risks', weightPercent: 44 },
    { topic: 'Understanding Trading, Customer Accounts and Prohibited Activities', weightPercent: 31 },
    { topic: 'Overview of the Regulatory Framework', weightPercent: 9 },
  ],
  SERIES_7: [
    { topic: 'Seeks Business for the Broker-Dealer', weightPercent: 7 },
    { topic: 'Opens Accounts and Evaluates Financial Profile', weightPercent: 9 },
    { topic: 'Provides Information, Recommendations, Transfers Assets', weightPercent: 73 },
    { topic: 'Obtains and Verifies Instructions; Processes Transactions', weightPercent: 11 },
  ],
  SERIES_57: [
    { topic: 'Trading Activities', weightPercent: 82 },
    { topic: 'Books and Records, Trade Reporting and Settlement', weightPercent: 18 },
  ],
  SERIES_65: [
    { topic: 'Regulations and Laws', weightPercent: 22 },
    { topic: 'Ethics and Fiduciary Duty', weightPercent: 20 },
    { topic: 'Investment Vehicles', weightPercent: 22 },
    { topic: 'Client Strategies and Economic Factors', weightPercent: 20 },
    { topic: 'Communications and Documentation', weightPercent: 16 },
  ],
}
