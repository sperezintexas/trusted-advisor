'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import AppHeader from '../components/AppHeader'
import AdminGuard from '../components/AdminGuard'
import { useAuth } from '@/lib/auth'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import {
  fetchAccessRequests,
  approveAccessRequest,
  rejectAccessRequest,
  fetchUsers,
  createUser,
  updateUser,
  deleteUser,
  fetchPersonaDocuments,
  uploadPersonaDocument,
  reindexPersonaDocument,
  reindexAllPersonaDocuments,
  deletePersonaDocument,
  generatePersonaQuestions,
  fetchCoachGenerationConfigs,
  updateCoachGenerationConfig,
  runCoachGenerationNow,
  runAllCoachGenerationNow,
  fetchAdminJobsOverview,
  retryRecommendationJob,
  type AccessRequestView,
  type AdminJobsOverviewResponse,
  type AdminDocumentView,
  type CoachGenerationConfigView,
  type GeneratedQuestionView,
  type UserView,
} from '@/lib/admin'

type ChatConfig = {
  debug?: boolean
  tools?: Record<string, boolean>
  context?: Record<string, string>
}

type GrokTestResult = {
  success: boolean
  message: string
}

type AuthDebug = {
  apiKeyConfigured: boolean
  authDebugEnabled: boolean
  userId: string | null
  username: string | null
}

type ConfigTab = 'system' | 'access-requests' | 'users' | 'rag-documents' | 'generate-questions' | 'jobs'

type Persona = { id: string; name: string; description?: string }
type UserFormState = {
  id: string | null
  email: string
  username: string
  displayName: string
  role: 'ADMIN' | 'BASIC' | 'PREMIUM'
  registered: boolean
}

type CoachGenerationDraft = {
  enabled: boolean
  personaId: string
  targetPoolSize: number
  intervalMinutes: number
}

export default function ConfigPage() {
  const { user } = useAuth()
  const [origin, setOrigin] = useState('')
  const [config, setConfig] = useState<ChatConfig>({})
  const [configLoading, setConfigLoading] = useState(true)
  const [configError, setConfigError] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<GrokTestResult | null>(null)
  const [testLoading, setTestLoading] = useState(false)
  const [authDebug, setAuthDebug] = useState<AuthDebug | null>(null)
  const [authDebugLoading, setAuthDebugLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<ConfigTab>('system')
  const [accessRequests, setAccessRequests] = useState<AccessRequestView[]>([])
  const [accessLoading, setAccessLoading] = useState(false)
  const [accessError, setAccessError] = useState<string | null>(null)
  const [processingRequestId, setProcessingRequestId] = useState<string | null>(null)
  const [users, setUsers] = useState<UserView[]>([])
  const [usersLoading, setUsersLoading] = useState(false)
  const [usersError, setUsersError] = useState<string | null>(null)
  const [savingUser, setSavingUser] = useState(false)
  const [deletingUserId, setDeletingUserId] = useState<string | null>(null)
  const [userForm, setUserForm] = useState<UserFormState>({
    id: null,
    email: '',
    username: '',
    displayName: '',
    role: 'BASIC',
    registered: false,
  })
  const [personas, setPersonas] = useState<Persona[]>([])
  const [personasLoading, setPersonasLoading] = useState(true)
  const [ragPersonaId, setRagPersonaId] = useState<string>('')
  const [ragDocuments, setRagDocuments] = useState<AdminDocumentView[]>([])
  const [ragDocumentsLoading, setRagDocumentsLoading] = useState(false)
  const [ragError, setRagError] = useState<string | null>(null)
  const [ragUploading, setRagUploading] = useState(false)
  const [ragActionDocId, setRagActionDocId] = useState<string | null>(null)
  const [ragReindexAllLoading, setRagReindexAllLoading] = useState(false)
  const [genQuestionsPersonaId, setGenQuestionsPersonaId] = useState<string>('')
  const [genQuestionsCount, setGenQuestionsCount] = useState(10)
  const [genQuestionsExamCode, setGenQuestionsExamCode] = useState<string>('')
  const [genQuestionsSaveToPool, setGenQuestionsSaveToPool] = useState(false)
  const [genQuestionsLoading, setGenQuestionsLoading] = useState(false)
  const [genQuestionsError, setGenQuestionsError] = useState<string | null>(null)
  const [generatedQuestions, setGeneratedQuestions] = useState<GeneratedQuestionView[]>([])
  const [coachGenConfigs, setCoachGenConfigs] = useState<CoachGenerationConfigView[]>([])
  const [coachGenDrafts, setCoachGenDrafts] = useState<Record<string, CoachGenerationDraft>>({})
  const [coachGenLoading, setCoachGenLoading] = useState(false)
  const [coachGenError, setCoachGenError] = useState<string | null>(null)
  const [coachGenSavingExam, setCoachGenSavingExam] = useState<string | null>(null)
  const [coachGenRunningExam, setCoachGenRunningExam] = useState<string | null>(null)
  const [coachGenRunningAll, setCoachGenRunningAll] = useState(false)
  const [jobsOverview, setJobsOverview] = useState<AdminJobsOverviewResponse | null>(null)
  const [jobsOverviewLoading, setJobsOverviewLoading] = useState(false)
  const [jobsOverviewError, setJobsOverviewError] = useState<string | null>(null)
  const [retryingAttemptId, setRetryingAttemptId] = useState<string | null>(null)

  useEffect(() => {
    setOrigin(window.location.origin)
  }, [])

  const loadConfig = useCallback(async () => {
    setConfigLoading(true)
    setConfigError(null)
    try {
      const res = await fetch(apiUrl('/chat/config'), defaultFetchOptions())
      if (!res.ok) throw new Error(`Config: ${res.status}`)
      const data = (await res.json()) as ChatConfig
      setConfig(data)
    } catch (e) {
      setConfigError(e instanceof Error ? e.message : 'Failed to load config')
    } finally {
      setConfigLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadConfig()
  }, [loadConfig])

  const loadAuthDebug = useCallback(async () => {
    setAuthDebugLoading(true)
    try {
      const res = await fetch(apiUrl('/debug/auth'), defaultFetchOptions())
      if (!res.ok) throw new Error(`${res.status}`)
      const data = (await res.json()) as AuthDebug
      setAuthDebug(data)
    } catch {
      setAuthDebug(null)
    } finally {
      setAuthDebugLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAuthDebug()
  }, [loadAuthDebug])

  const setDebug = useCallback(
    async (debug: boolean) => {
      const next = { ...config, debug }
      setConfig(next)
      try {
        const res = await fetch(apiUrl('/chat/config'), defaultFetchOptions({
          method: 'PUT',
          body: JSON.stringify(next),
        }))
        if (!res.ok) throw new Error(`Save: ${res.status}`)
      } catch (e) {
        setConfigError(e instanceof Error ? e.message : 'Failed to save config')
      }
    },
    [config]
  )

  const testConnection = useCallback(async () => {
    setTestLoading(true)
    setTestResult(null)
    try {
      const res = await fetch(apiUrl('/chat/config/test'), defaultFetchOptions())
      const data = (await res.json()) as GrokTestResult
      setTestResult(data)
    } catch (e) {
      setTestResult({
        success: false,
        message: e instanceof Error ? e.message : 'Request failed',
      })
    } finally {
      setTestLoading(false)
    }
  }, [])

  const loadAccessRequests = useCallback(async () => {
    setAccessLoading(true)
    setAccessError(null)
    const result = await fetchAccessRequests('PENDING')
    if (result === null) {
      setAccessError('Failed to load pending access requests.')
      setAccessRequests([])
    } else {
      setAccessRequests(result.requests)
    }
    setAccessLoading(false)
  }, [])

  const handleApprove = useCallback(async (id: string) => {
    setProcessingRequestId(id)
    const result = await approveAccessRequest(id, 'Approved from config admin tab')
    setProcessingRequestId(null)
    if (!result?.success) {
      setAccessError(result?.message || 'Failed to approve request.')
      return
    }
    await loadAccessRequests()
  }, [loadAccessRequests])

  const handleReject = useCallback(async (id: string) => {
    setProcessingRequestId(id)
    const result = await rejectAccessRequest(id, 'Rejected from config admin tab')
    setProcessingRequestId(null)
    if (!result?.success) {
      setAccessError(result?.message || 'Failed to reject request.')
      return
    }
    await loadAccessRequests()
  }, [loadAccessRequests])

  useEffect(() => {
    if (activeTab !== 'access-requests') return
    void loadAccessRequests()
  }, [activeTab, loadAccessRequests])

  const loadUsers = useCallback(async () => {
    setUsersLoading(true)
    setUsersError(null)
    const result = await fetchUsers()
    if (result === null) {
      setUsersError('Failed to load users.')
      setUsers([])
    } else {
      setUsers(result.users)
    }
    setUsersLoading(false)
  }, [])

  useEffect(() => {
    if (activeTab === 'users') void loadUsers()
  }, [activeTab, loadUsers])

  const handleDeleteUser = useCallback(
    async (id: string, email: string) => {
      if (!confirm(`Delete user "${email}"? This cannot be undone.`)) return
      setDeletingUserId(id)
      setUsersError(null)
      const result = await deleteUser(id)
      setDeletingUserId(null)
      if (result?.success) void loadUsers()
      else if (result && !result.success) setUsersError(result.message)
      else setUsersError('Delete failed.')
    },
    [loadUsers]
  )

  const resetUserForm = useCallback(() => {
    setUserForm({
      id: null,
      email: '',
      username: '',
      displayName: '',
      role: 'BASIC',
      registered: false,
    })
  }, [])

  const handleEditUser = useCallback((u: UserView) => {
    setUsersError(null)
    setUserForm({
      id: u.id,
      email: u.email,
      username: u.username,
      displayName: u.displayName ?? '',
      role: (u.role === 'ADMIN' || u.role === 'PREMIUM' ? u.role : 'BASIC'),
      registered: u.registered,
    })
  }, [])

  const handleSaveUser = useCallback(async () => {
    const email = userForm.email.trim().toLowerCase()
    if (!email || !email.includes('@')) {
      setUsersError('Valid email is required.')
      return
    }

    setSavingUser(true)
    setUsersError(null)
    const payload = {
      email,
      username: userForm.username.trim() || undefined,
      displayName: userForm.displayName.trim() || undefined,
      role: userForm.role,
      registered: userForm.registered,
    } as const

    const result = userForm.id
      ? await updateUser(userForm.id, payload)
      : await createUser(payload)

    setSavingUser(false)
    if (!result) {
      setUsersError(userForm.id ? 'Failed to update user.' : 'Failed to add user.')
      return
    }
    if (!result.success) {
      setUsersError(result.message)
      return
    }
    resetUserForm()
    await loadUsers()
  }, [userForm, loadUsers, resetUserForm])

  const loadPersonas = useCallback(async () => {
    setPersonasLoading(true)
    try {
      const res = await fetch(apiUrl('/personas'), defaultFetchOptions())
      if (!res.ok) throw new Error(`${res.status}`)
      const data = (await res.json()) as Persona[]
      setPersonas(data)
      setRagPersonaId((prev) => {
        if (prev) return prev
        if (data.length === 0) return ''
        const fintech = data.find(
          (p) =>
            p.name.toLowerCase().includes('fintech') ||
            p.name.toLowerCase().includes('advisor') ||
            p.name.toLowerCase().includes('finance expert')
        )
        return fintech?.id ?? data[0].id
      })
      setGenQuestionsPersonaId((prev) => {
        if (prev) return prev
        if (data.length === 0) return ''
        const financeExpert = data.find(
          (p) =>
            p.name.toLowerCase().includes('finance expert') ||
            p.name.toLowerCase().includes('fintech') ||
            p.name.toLowerCase().includes('advisor')
        )
        return financeExpert?.id ?? data[0].id
      })
    } catch {
      setPersonas([])
    } finally {
      setPersonasLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadPersonas()
  }, [loadPersonas])

  const loadRagDocuments = useCallback(async () => {
    if (!ragPersonaId) {
      setRagDocuments([])
      return
    }
    setRagDocumentsLoading(true)
    setRagError(null)
    const result = await fetchPersonaDocuments(ragPersonaId)
    if (result === null) {
      setRagError('Failed to load documents.')
      setRagDocuments([])
    } else {
      setRagDocuments(result.documents)
    }
    setRagDocumentsLoading(false)
  }, [ragPersonaId])

  useEffect(() => {
    if (activeTab === 'rag-documents' && ragPersonaId) void loadRagDocuments()
  }, [activeTab, ragPersonaId, loadRagDocuments])

  const handleRagUpload = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file || !ragPersonaId) return
      e.target.value = ''
      setRagUploading(true)
      setRagError(null)
      const result = await uploadPersonaDocument(ragPersonaId, file)
      setRagUploading(false)
      if (!result) {
        setRagError('Upload failed.')
        return
      }
      if (!result.success) {
        setRagError(result.message)
        return
      }
      await loadRagDocuments()
    },
    [ragPersonaId, loadRagDocuments]
  )

  const handleRagReindex = useCallback(
    async (docId: string) => {
      if (!ragPersonaId) return
      setRagActionDocId(docId)
      setRagError(null)
      const result = await reindexPersonaDocument(ragPersonaId, docId, '')
      setRagActionDocId(null)
      if (result?.success) void loadRagDocuments()
      else if (result && !result.success) setRagError(result.message)
    },
    [ragPersonaId, loadRagDocuments]
  )

  const handleRagReindexAll = useCallback(async () => {
    if (!ragPersonaId) return
    setRagReindexAllLoading(true)
    setRagError(null)
    const result = await reindexAllPersonaDocuments(ragPersonaId)
    setRagReindexAllLoading(false)
    if (!result) {
      setRagError('Failed to queue persona reindex job.')
      return
    }
    if (!result.success) {
      setRagError(result.message)
      return
    }
    await loadRagDocuments()
  }, [ragPersonaId, loadRagDocuments])

  const handleRagDelete = useCallback(
    async (docId: string) => {
      if (!ragPersonaId) return
      if (!confirm('Delete this document and its chunks?')) return
      setRagActionDocId(docId)
      setRagError(null)
      const result = await deletePersonaDocument(ragPersonaId, docId)
      setRagActionDocId(null)
      if (result?.success) void loadRagDocuments()
      else if (result && !result.success) setRagError(result.message)
    },
    [ragPersonaId, loadRagDocuments]
  )

  const handleGenerateQuestions = useCallback(async () => {
    if (!genQuestionsPersonaId) return
    setGenQuestionsLoading(true)
    setGenQuestionsError(null)
    setGeneratedQuestions([])
    const result = await generatePersonaQuestions(
      genQuestionsPersonaId,
      genQuestionsCount,
      {
        examCode: genQuestionsExamCode || undefined,
        saveToPool: genQuestionsSaveToPool && !!genQuestionsExamCode,
      }
    )
    setGenQuestionsLoading(false)
    if (!result) {
      setGenQuestionsError('Request failed.')
      return
    }
    if (!result.success) {
      setGenQuestionsError(result.message)
      return
    }
    setGeneratedQuestions(result.questions)
  }, [genQuestionsPersonaId, genQuestionsCount, genQuestionsExamCode, genQuestionsSaveToPool])

  const loadCoachGenerationConfigs = useCallback(async () => {
    setCoachGenLoading(true)
    setCoachGenError(null)
    const result = await fetchCoachGenerationConfigs()
    if (!result) {
      setCoachGenError('Failed to load generation job configs.')
      setCoachGenConfigs([])
      setCoachGenLoading(false)
      return
    }
    setCoachGenConfigs(result.configs)
    setCoachGenDrafts((prev) => {
      const next = { ...prev }
      for (const cfg of result.configs) {
        if (!next[cfg.examCode]) {
          next[cfg.examCode] = {
            enabled: cfg.enabled,
            personaId: cfg.personaId,
            targetPoolSize: cfg.targetPoolSize,
            intervalMinutes: cfg.intervalMinutes,
          }
        }
      }
      return next
    })
    setCoachGenLoading(false)
  }, [])

  useEffect(() => {
    if (activeTab === 'generate-questions' || activeTab === 'jobs') {
      void loadCoachGenerationConfigs()
    }
  }, [activeTab, loadCoachGenerationConfigs])

  const loadJobsOverview = useCallback(async () => {
    setJobsOverviewLoading(true)
    setJobsOverviewError(null)
    const result = await fetchAdminJobsOverview()
    if (!result) {
      setJobsOverviewError('Failed to load job overview.')
      setJobsOverview(null)
      setJobsOverviewLoading(false)
      return
    }
    setJobsOverview(result)
    setJobsOverviewLoading(false)
  }, [])

  useEffect(() => {
    if (activeTab === 'jobs') void loadJobsOverview()
  }, [activeTab, loadJobsOverview])

  const updateCoachDraft = useCallback((examCode: string, patch: Partial<CoachGenerationDraft>) => {
    setCoachGenDrafts((prev) => {
      const current = prev[examCode]
      if (!current) return prev
      return {
        ...prev,
        [examCode]: { ...current, ...patch },
      }
    })
  }, [])

  const saveCoachGenerationConfig = useCallback(async (examCode: string) => {
    const draft = coachGenDrafts[examCode]
    if (!draft) return
    setCoachGenSavingExam(examCode)
    setCoachGenError(null)
    const updated = await updateCoachGenerationConfig(examCode, {
      enabled: draft.enabled,
      personaId: draft.personaId,
      targetPoolSize: Math.max(25, draft.targetPoolSize),
      intervalMinutes: Math.max(1, draft.intervalMinutes),
    })
    setCoachGenSavingExam(null)
    if (!updated) {
      setCoachGenError(`Failed to save ${examCode} config.`)
      return
    }
    await loadCoachGenerationConfigs()
  }, [coachGenDrafts, loadCoachGenerationConfigs])

  const runCoachGeneration = useCallback(async (examCode: string) => {
    setCoachGenRunningExam(examCode)
    setCoachGenError(null)
    const updated = await runCoachGenerationNow(examCode)
    setCoachGenRunningExam(null)
    if (!updated) {
      setCoachGenError(`Failed to queue ${examCode} generation.`)
      return
    }
    await loadCoachGenerationConfigs()
  }, [loadCoachGenerationConfigs])

  const runAllCoachGeneration = useCallback(async () => {
    setCoachGenRunningAll(true)
    setCoachGenError(null)
    const updated = await runAllCoachGenerationNow()
    setCoachGenRunningAll(false)
    if (!updated) {
      setCoachGenError('Failed to queue all exam generation jobs.')
      return
    }
    await loadCoachGenerationConfigs()
    await loadJobsOverview()
  }, [loadCoachGenerationConfigs, loadJobsOverview])

  const retryFailedRecommendation = useCallback(async (attemptId: string) => {
    setRetryingAttemptId(attemptId)
    setJobsOverviewError(null)
    const result = await retryRecommendationJob(attemptId)
    setRetryingAttemptId(null)
    if (!result?.success) {
      setJobsOverviewError(result?.message || 'Failed to retry recommendation job.')
      return
    }
    await loadJobsOverview()
  }, [loadJobsOverview])

  const copyGeneratedQuestionsJson = useCallback(() => {
    const json = JSON.stringify(generatedQuestions, null, 2)
    void navigator.clipboard.writeText(json)
  }, [generatedQuestions])

  const formatDate = (dateStr: string): string => {
    const parsed = new Date(dateStr)
    if (Number.isNaN(parsed.getTime())) return dateStr
    return parsed.toLocaleString()
  }

  return (
    <AdminGuard>
      <div
        className="flex min-h-screen flex-col bg-white"
        style={{ backgroundImage: 'none' }}
      >
        <AppHeader />
        <main className="mx-auto w-full max-w-3xl px-4 py-10">
          <h1 className="mb-2 text-2xl font-semibold text-[var(--docs-text)]">
          Configuration
        </h1>
          <p className="mb-6 text-sm text-[var(--docs-muted)]">
          Prompt and persona options. Modeled after the{' '}
          <a
            href="https://docs.x.ai/developers/api-reference"
            target="_blank"
            rel="noopener noreferrer"
            className="text-[var(--docs-accent)] hover:underline"
          >
            xAI REST API Reference
          </a>
          .
        </p>

          <div className="mb-8 flex items-center gap-2 border-b border-[var(--docs-border)]">
            <button
              type="button"
              onClick={() => setActiveTab('system')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'system'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              System
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('access-requests')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'access-requests'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              Access Requests
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('users')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'users'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              Users
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('rag-documents')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'rag-documents'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              RAG Documents
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('generate-questions')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'generate-questions'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              Generate Questions
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('jobs')}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium ${
                activeTab === 'jobs'
                  ? 'border border-b-0 border-[var(--docs-border)] bg-white text-[var(--docs-text)]'
                  : 'text-[var(--docs-muted)] hover:text-[var(--docs-text)]'
              }`}
            >
              Jobs
            </button>
          </div>

          {activeTab === 'generate-questions' && (
            <section className="mb-12">
              <div className="mb-3">
                <h2 className="text-lg font-medium text-[var(--docs-text)]">
                  Generate test questions
                </h2>
                <p className="text-sm text-[var(--docs-muted)]">
                  Generate multiple-choice practice questions from the indexed RAG documents. Select an exam to tag questions by FINRA topic and save to the practice exam pool; practice exams then draw from the pool by topic %.
                </p>
              </div>
              <div className="docs-path mb-3 inline-block">
                POST /api/admin/personas/:personaId/generate-questions
              </div>
              {genQuestionsError && (
                <p className="mb-2 text-sm text-red-600" role="alert">
                  {genQuestionsError}
                </p>
              )}
              <div className="mb-4 flex flex-wrap items-center gap-4">
                <label className="flex items-center gap-2 text-sm text-[var(--docs-text)]">
                  Persona:
                  <select
                    value={genQuestionsPersonaId}
                    onChange={(e) => setGenQuestionsPersonaId(e.target.value)}
                    disabled={personasLoading}
                    className="rounded border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
                  >
                    <option value="">Select persona</option>
                    {personas.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center gap-2 text-sm text-[var(--docs-text)]">
                  Exam (for topic % and pool):
                  <select
                    value={genQuestionsExamCode}
                    onChange={(e) => setGenQuestionsExamCode(e.target.value)}
                    className="rounded border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
                  >
                    <option value="">None (preview only)</option>
                    <option value="SIE">SIE</option>
                    <option value="SERIES_7">Series 7</option>
                    <option value="SERIES_57">Series 57</option>
                    <option value="SERIES_65">Series 65</option>
                  </select>
                </label>
                <label className="flex items-center gap-2 text-sm text-[var(--docs-text)]">
                  Number of questions:
                  <input
                    type="number"
                    min={1}
                    max={25}
                    value={genQuestionsCount}
                    onChange={(e) =>
                      setGenQuestionsCount(
                        Math.min(25, Math.max(1, parseInt(e.target.value, 10) || 10))
                      )
                    }
                    className="w-20 rounded border border-[var(--docs-border)] bg-white px-2 py-2 text-sm"
                  />
                </label>
                <label className="flex cursor-pointer items-center gap-2 text-sm text-[var(--docs-text)]">
                  <input
                    type="checkbox"
                    checked={genQuestionsSaveToPool}
                    onChange={(e) => setGenQuestionsSaveToPool(e.target.checked)}
                    disabled={!genQuestionsExamCode}
                    className="h-4 w-4 rounded border-[var(--docs-border)]"
                  />
                  Save to exam pool
                </label>
                <button
                  type="button"
                  onClick={() => void handleGenerateQuestions()}
                  disabled={!genQuestionsPersonaId || genQuestionsLoading}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {genQuestionsLoading ? 'Generating…' : 'Generate'}
                </button>
                {generatedQuestions.length > 0 && (
                  <button
                    type="button"
                    onClick={copyGeneratedQuestionsJson}
                    className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                  >
                    Copy JSON
                  </button>
                )}
              </div>
              {generatedQuestions.length > 0 && (
                <div className="space-y-6 rounded-lg border border-[var(--docs-border)] bg-white p-4">
                  <h3 className="text-sm font-medium text-[var(--docs-muted)]">
                    Generated questions ({generatedQuestions.length})
                  </h3>
                  {generatedQuestions.map((q, idx) => (
                    <div
                      key={idx}
                      className="border-b border-[var(--docs-border)] pb-4 last:border-b-0 last:pb-0"
                    >
                      <p className="mb-2 font-medium text-[var(--docs-text)]">
                        {idx + 1}. {q.question}
                      </p>
                      <ul className="mb-2 list-inside list-disc text-sm text-[var(--docs-muted)]">
                        {q.choices.map((c) => (
                          <li key={c.letter}>
                            <span className={c.letter === q.correctLetter ? 'font-medium text-green-700' : ''}>
                              {c.letter}. {c.text}
                              {c.letter === q.correctLetter ? ' ✓' : ''}
                            </span>
                          </li>
                        ))}
                      </ul>
                      {q.topic && (
                        <p className="mb-1 text-xs text-[var(--docs-muted)]">
                          <strong>Topic:</strong> {q.topic}
                        </p>
                      )}
                      <p className="text-xs text-[var(--docs-muted)]">
                        <strong>Explanation:</strong> {q.explanation}
                      </p>
                    </div>
                  ))}
                </div>
              )}

              <div className="mt-8">
                <h3 className="text-base font-medium text-[var(--docs-text)]">
                  Background exam pool generation
                </h3>
                <p className="mb-3 text-sm text-[var(--docs-muted)]">
                  Admin-managed async jobs generate exam pools in fixed 25-question chunks. Use Run now to queue immediate chunked generation until target pool size is reached.
                </p>
                <div className="docs-path mb-3 inline-block">
                  GET/PUT /api/admin/coach/generation/configs · POST /api/admin/coach/generation/configs/:examCode/run
                </div>
                {coachGenError && (
                  <p className="mb-2 text-sm text-red-600" role="alert">
                    {coachGenError}
                  </p>
                )}
                <div className="mb-3">
                  <button
                    type="button"
                    onClick={() => void loadCoachGenerationConfigs()}
                    disabled={coachGenLoading}
                    className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                  >
                    {coachGenLoading ? 'Refreshing…' : 'Refresh jobs'}
                  </button>
                </div>
                <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                  {coachGenLoading && coachGenConfigs.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">Loading generation jobs…</div>
                  ) : coachGenConfigs.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">No generation configs found.</div>
                  ) : (
                    <table className="min-w-full divide-y divide-[var(--docs-border)]">
                      <thead className="bg-[var(--docs-code-bg)]">
                        <tr>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Exam</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Enabled</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Persona</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Pool</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Target</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Interval (min)</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Status</th>
                          <th className="px-3 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--docs-border)]">
                        {coachGenConfigs.map((cfg) => {
                          const draft = coachGenDrafts[cfg.examCode] ?? {
                            enabled: cfg.enabled,
                            personaId: cfg.personaId,
                            targetPoolSize: cfg.targetPoolSize,
                            intervalMinutes: cfg.intervalMinutes,
                          }
                          return (
                            <tr key={cfg.examCode}>
                              <td className="px-3 py-3 text-sm font-medium text-[var(--docs-text)]">
                                {cfg.examCode}
                                <p className="text-xs text-[var(--docs-muted)]">Chunk size: {cfg.chunkSize}</p>
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <input
                                  type="checkbox"
                                  checked={draft.enabled}
                                  onChange={(e) => updateCoachDraft(cfg.examCode, { enabled: e.target.checked })}
                                  className="h-4 w-4 rounded border-[var(--docs-border)]"
                                />
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <select
                                  value={draft.personaId}
                                  onChange={(e) => updateCoachDraft(cfg.examCode, { personaId: e.target.value })}
                                  className="rounded border border-[var(--docs-border)] bg-white px-2 py-1 text-xs"
                                >
                                  {personas.map((p) => (
                                    <option key={p.id} value={p.id}>
                                      {p.name}
                                    </option>
                                  ))}
                                </select>
                              </td>
                              <td className="px-3 py-3 text-sm text-[var(--docs-muted)]">
                                {cfg.currentPoolSize}
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <input
                                  type="number"
                                  min={25}
                                  max={5000}
                                  value={draft.targetPoolSize}
                                  onChange={(e) =>
                                    updateCoachDraft(cfg.examCode, {
                                      targetPoolSize: Math.max(25, parseInt(e.target.value, 10) || 25),
                                    })
                                  }
                                  className="w-20 rounded border border-[var(--docs-border)] bg-white px-2 py-1 text-xs"
                                />
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <input
                                  type="number"
                                  min={1}
                                  max={1440}
                                  value={draft.intervalMinutes}
                                  onChange={(e) =>
                                    updateCoachDraft(cfg.examCode, {
                                      intervalMinutes: Math.max(1, parseInt(e.target.value, 10) || 1),
                                    })
                                  }
                                  className="w-20 rounded border border-[var(--docs-border)] bg-white px-2 py-1 text-xs"
                                />
                              </td>
                              <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">
                                <p>{cfg.running ? 'RUNNING' : (cfg.lastStatus ?? '—')}</p>
                                <p>{cfg.lastMessage ?? '—'}</p>
                                <p>Next: {formatDate(cfg.nextRunAt)}</p>
                              </td>
                              <td className="px-3 py-3">
                                <div className="flex items-center justify-end gap-2">
                                  <button
                                    type="button"
                                    onClick={() => void saveCoachGenerationConfig(cfg.examCode)}
                                    disabled={coachGenSavingExam === cfg.examCode}
                                    className="rounded-lg border border-[var(--docs-border)] bg-white px-2 py-1 text-xs font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                                  >
                                    {coachGenSavingExam === cfg.examCode ? 'Saving…' : 'Save'}
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => void runCoachGeneration(cfg.examCode)}
                                    disabled={coachGenRunningExam === cfg.examCode}
                                    className="rounded-lg bg-[var(--docs-accent)] px-2 py-1 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50"
                                  >
                                    {coachGenRunningExam === cfg.examCode ? 'Queueing…' : 'Run now'}
                                  </button>
                                </div>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            </section>
          )}

          {activeTab === 'jobs' && (
            <section className="mb-12">
              <div className="mb-3">
                <h2 className="text-lg font-medium text-[var(--docs-text)]">
                  Async Jobs
                </h2>
                <p className="text-sm text-[var(--docs-muted)]">
                  Monitor and manage long-running background jobs for recommendation generation, persona ingestion, and exam question pool generation.
                </p>
              </div>
              <div className="docs-path mb-3 inline-block">
                GET /api/admin/jobs/overview · POST /api/admin/jobs/recommendations/:attemptId/retry
              </div>
              {(jobsOverviewError || coachGenError) && (
                <p className="mb-2 text-sm text-red-600" role="alert">
                  {jobsOverviewError || coachGenError}
                </p>
              )}
              <div className="mb-4 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    void loadJobsOverview()
                    void loadCoachGenerationConfigs()
                  }}
                  disabled={jobsOverviewLoading || coachGenLoading}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {(jobsOverviewLoading || coachGenLoading) ? 'Refreshing…' : 'Refresh jobs'}
                </button>
                <button
                  type="button"
                  onClick={() => void runAllCoachGeneration()}
                  disabled={coachGenRunningAll}
                  className="rounded-lg bg-[var(--docs-accent)] px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
                >
                  {coachGenRunningAll ? 'Queueing all…' : 'Run all exams now'}
                </button>
              </div>

              <div className="mb-6 grid gap-3 md:grid-cols-2 xl:grid-cols-7">
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Recommendation queued</p>
                  <p className="text-lg font-semibold text-[var(--docs-text)]">
                    {jobsOverview?.recommendationQueue.queued ?? 0}
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Recommendation processing</p>
                  <p className="text-lg font-semibold text-[var(--docs-text)]">
                    {jobsOverview?.recommendationQueue.processing ?? 0}
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Recommendation failed</p>
                  <p className="text-lg font-semibold text-red-700">
                    {jobsOverview?.recommendationQueue.failed ?? 0}
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Recommendation ready</p>
                  <p className="text-lg font-semibold text-green-700">
                    {jobsOverview?.recommendationQueue.ready ?? 0}
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Persona ingestion queued</p>
                  <p className="text-lg font-semibold text-[var(--docs-text)]">
                    {jobsOverview?.personaIngestion.queued ?? 0}
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Persona ingestion indexing</p>
                  <p className="text-lg font-semibold text-[var(--docs-text)]">
                    {jobsOverview?.personaIngestion.indexing ?? 0}
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--docs-border)] bg-white p-3">
                  <p className="text-xs text-[var(--docs-muted)]">Persona ingestion failed</p>
                  <p className="text-lg font-semibold text-red-700">
                    {jobsOverview?.personaIngestion.failed ?? 0}
                  </p>
                </div>
              </div>

              <div className="mb-8">
                <h3 className="mb-2 text-base font-medium text-[var(--docs-text)]">
                  Recommendation queue activity
                </h3>
                <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                  {jobsOverviewLoading && !jobsOverview ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">Loading recommendation jobs…</div>
                  ) : !jobsOverview || jobsOverview.recommendationQueue.recent.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">No recent recommendation jobs.</div>
                  ) : (
                    <table className="min-w-full divide-y divide-[var(--docs-border)]">
                      <thead className="bg-[var(--docs-code-bg)]">
                        <tr>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Attempt</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Exam</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Status</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Attempts</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Error</th>
                          <th className="px-3 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--docs-border)]">
                        {jobsOverview.recommendationQueue.recent.map((job) => (
                          <tr key={job.attemptId}>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{job.attemptId}</td>
                            <td className="px-3 py-3 text-sm text-[var(--docs-text)]">{job.examCode}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{job.recommendationStatus}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{job.recommendationAttempts}</td>
                            <td className="px-3 py-3 text-xs text-red-700">{job.recommendationError ?? '—'}</td>
                            <td className="px-3 py-3">
                              <div className="flex items-center justify-end">
                                {job.recommendationStatus === 'FAILED' ? (
                                  <button
                                    type="button"
                                    onClick={() => void retryFailedRecommendation(job.attemptId)}
                                    disabled={retryingAttemptId === job.attemptId}
                                    className="rounded-lg border border-[var(--docs-border)] bg-white px-2 py-1 text-xs font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                                  >
                                    {retryingAttemptId === job.attemptId ? 'Retrying…' : 'Retry'}
                                  </button>
                                ) : (
                                  <span className="text-xs text-[var(--docs-muted)]">—</span>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>

              <div className="mb-8">
                <h3 className="mb-2 text-base font-medium text-[var(--docs-text)]">
                  Full exam daily cache
                </h3>
                <p className="mb-2 text-sm text-[var(--docs-muted)]">
                  Full exam sessions are generated at most once per exam per UTC day and reused from cache.
                </p>
                <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                  {jobsOverviewLoading && !jobsOverview ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">Loading cache status…</div>
                  ) : !jobsOverview || jobsOverview.fullExamCache.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">No full exam cache records.</div>
                  ) : (
                    <table className="min-w-full divide-y divide-[var(--docs-border)]">
                      <thead className="bg-[var(--docs-code-bg)]">
                        <tr>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Exam</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Today cached</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Cache date</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Questions</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Expected</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Generated</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--docs-border)]">
                        {jobsOverview.fullExamCache.map((cache) => (
                          <tr key={cache.examCode}>
                            <td className="px-3 py-3 text-sm font-medium text-[var(--docs-text)]">{cache.examCode}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">
                              {cache.hasTodayCache ? 'YES' : 'NO'}
                            </td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{cache.cacheDate ?? '—'}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{cache.questionCount ?? '—'}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{cache.expectedQuestionCount}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">
                              {cache.generatedAt ? formatDate(cache.generatedAt) : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>

              <div className="mb-8">
                <h3 className="mb-2 text-base font-medium text-[var(--docs-text)]">
                  Recent persona ingestion failures
                </h3>
                <p className="mb-2 text-sm text-[var(--docs-muted)]">
                  Failed file ingestion jobs with extraction or chunking errors.
                </p>
                <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                  {jobsOverviewLoading && !jobsOverview ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">Loading ingestion failures…</div>
                  ) : !jobsOverview || jobsOverview.personaIngestion.recentFailures.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">No recent ingestion failures.</div>
                  ) : (
                    <table className="min-w-full divide-y divide-[var(--docs-border)]">
                      <thead className="bg-[var(--docs-code-bg)]">
                        <tr>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">File</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Persona</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Source</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Updated</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Error</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--docs-border)]">
                        {jobsOverview.personaIngestion.recentFailures.map((job) => (
                          <tr key={job.fileId}>
                            <td className="px-3 py-3 text-sm text-[var(--docs-text)]">{job.fileName}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{job.personaId}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{job.sourceType}</td>
                            <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">{formatDate(job.updatedAt)}</td>
                            <td className="px-3 py-3 text-xs text-red-700">{job.lastError}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>

              <div>
                <h3 className="mb-2 text-base font-medium text-[var(--docs-text)]">
                  Exam pool generation jobs
                </h3>
                <div className="docs-path mb-3 inline-block">
                  GET/PUT /api/admin/coach/generation/configs · POST /api/admin/coach/generation/configs/:examCode/run
                </div>
                <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                  {coachGenLoading && coachGenConfigs.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">Loading generation jobs…</div>
                  ) : coachGenConfigs.length === 0 ? (
                    <div className="p-4 text-sm text-[var(--docs-muted)]">No generation configs found.</div>
                  ) : (
                    <table className="min-w-full divide-y divide-[var(--docs-border)]">
                      <thead className="bg-[var(--docs-code-bg)]">
                        <tr>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Exam</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Enabled</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Persona</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Pool</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Target</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Interval (min)</th>
                          <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Status</th>
                          <th className="px-3 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--docs-border)]">
                        {coachGenConfigs.map((cfg) => {
                          const draft = coachGenDrafts[cfg.examCode] ?? {
                            enabled: cfg.enabled,
                            personaId: cfg.personaId,
                            targetPoolSize: cfg.targetPoolSize,
                            intervalMinutes: cfg.intervalMinutes,
                          }
                          return (
                            <tr key={cfg.examCode}>
                              <td className="px-3 py-3 text-sm font-medium text-[var(--docs-text)]">
                                {cfg.examCode}
                                <p className="text-xs text-[var(--docs-muted)]">Chunk size: {cfg.chunkSize}</p>
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <input
                                  type="checkbox"
                                  checked={draft.enabled}
                                  onChange={(e) => updateCoachDraft(cfg.examCode, { enabled: e.target.checked })}
                                  className="h-4 w-4 rounded border-[var(--docs-border)]"
                                />
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <select
                                  value={draft.personaId}
                                  onChange={(e) => updateCoachDraft(cfg.examCode, { personaId: e.target.value })}
                                  className="rounded border border-[var(--docs-border)] bg-white px-2 py-1 text-xs"
                                >
                                  {personas.map((p) => (
                                    <option key={p.id} value={p.id}>
                                      {p.name}
                                    </option>
                                  ))}
                                </select>
                              </td>
                              <td className="px-3 py-3 text-sm text-[var(--docs-muted)]">
                                {cfg.currentPoolSize}
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <input
                                  type="number"
                                  min={25}
                                  max={5000}
                                  value={draft.targetPoolSize}
                                  onChange={(e) =>
                                    updateCoachDraft(cfg.examCode, {
                                      targetPoolSize: Math.max(25, parseInt(e.target.value, 10) || 25),
                                    })
                                  }
                                  className="w-20 rounded border border-[var(--docs-border)] bg-white px-2 py-1 text-xs"
                                />
                              </td>
                              <td className="px-3 py-3 text-sm">
                                <input
                                  type="number"
                                  min={1}
                                  max={1440}
                                  value={draft.intervalMinutes}
                                  onChange={(e) =>
                                    updateCoachDraft(cfg.examCode, {
                                      intervalMinutes: Math.max(1, parseInt(e.target.value, 10) || 1),
                                    })
                                  }
                                  className="w-20 rounded border border-[var(--docs-border)] bg-white px-2 py-1 text-xs"
                                />
                              </td>
                              <td className="px-3 py-3 text-xs text-[var(--docs-muted)]">
                                <p>{cfg.running ? 'RUNNING' : (cfg.lastStatus ?? '—')}</p>
                                <p>{cfg.lastMessage ?? '—'}</p>
                                <p>Next: {formatDate(cfg.nextRunAt)}</p>
                              </td>
                              <td className="px-3 py-3">
                                <div className="flex items-center justify-end gap-2">
                                  <button
                                    type="button"
                                    onClick={() => void saveCoachGenerationConfig(cfg.examCode)}
                                    disabled={coachGenSavingExam === cfg.examCode}
                                    className="rounded-lg border border-[var(--docs-border)] bg-white px-2 py-1 text-xs font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                                  >
                                    {coachGenSavingExam === cfg.examCode ? 'Saving…' : 'Save'}
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => void runCoachGeneration(cfg.examCode)}
                                    disabled={coachGenRunningExam === cfg.examCode}
                                    className="rounded-lg bg-[var(--docs-accent)] px-2 py-1 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50"
                                  >
                                    {coachGenRunningExam === cfg.examCode ? 'Queueing…' : 'Run now'}
                                  </button>
                                </div>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            </section>
          )}

          {activeTab === 'rag-documents' && (
            <section className="mb-12">
              <div className="mb-3">
                <h2 className="text-lg font-medium text-[var(--docs-text)]">
                  RAG Documents
                </h2>
                <p className="text-sm text-[var(--docs-muted)]">
                  Upload and index PDF, text, or markdown files for the selected persona. Chunks are used as context in chat.
                </p>
              </div>
              <div className="docs-path mb-3 inline-block">
                GET/POST /api/admin/personas/:personaId/documents · POST .../documents/:docId/index · POST .../documents/reindex-all · DELETE .../documents/:docId
              </div>
              {ragError && (
                <p className="mb-2 text-sm text-red-600" role="alert">
                  {ragError}
                </p>
              )}
              <div className="mb-4 flex flex-wrap items-center gap-4">
                <label className="flex items-center gap-2 text-sm text-[var(--docs-text)]">
                  Persona:
                  <select
                    value={ragPersonaId}
                    onChange={(e) => setRagPersonaId(e.target.value)}
                    disabled={personasLoading}
                    className="rounded border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
                  >
                    <option value="">Select persona</option>
                    {personas.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="cursor-pointer rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50">
                  <input
                    type="file"
                    accept=".txt,.md,.markdown,.pdf,text/plain,text/markdown,application/pdf"
                    className="sr-only"
                    onChange={handleRagUpload}
                    disabled={!ragPersonaId || ragUploading}
                  />
                  {ragUploading ? 'Uploading…' : 'Upload file'}
                </label>
                <button
                  type="button"
                  onClick={() => void loadRagDocuments()}
                  disabled={ragDocumentsLoading || !ragPersonaId}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {ragDocumentsLoading ? 'Refreshing…' : 'Refresh'}
                </button>
                <button
                  type="button"
                  onClick={() => void handleRagReindexAll()}
                  disabled={!ragPersonaId || ragReindexAllLoading}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {ragReindexAllLoading ? 'Queueing all…' : 'Reindex all files'}
                </button>
              </div>
              <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                {ragDocumentsLoading && !ragDocuments.length ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">
                    Loading documents…
                  </div>
                ) : !ragPersonaId ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">
                    Select a persona to list documents.
                  </div>
                ) : ragDocuments.length === 0 ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">
                    No documents. Upload a .txt, .md, or .pdf file.
                  </div>
                ) : (
                  <table className="min-w-full divide-y divide-[var(--docs-border)]">
                    <thead className="bg-[var(--docs-code-bg)]">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Name</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Status</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Chunks</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Indexed</th>
                        <th className="px-4 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--docs-border)]">
                      {ragDocuments.map((doc) => (
                        <tr key={doc.id}>
                          <td className="px-4 py-3 text-sm font-medium text-[var(--docs-text)]">
                            {doc.name}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {doc.status}
                            {doc.lastError && (
                              <span className="ml-1 text-red-600" title={doc.lastError}>
                                (!)
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {doc.chunkCount}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {formatDate(doc.updatedAt)}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => void handleRagReindex(doc.id)}
                                disabled={ragActionDocId === doc.id || doc.status !== 'INDEXED'}
                                className="rounded-lg border border-[var(--docs-border)] bg-white px-2 py-1 text-xs font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                              >
                                Reindex
                              </button>
                              <button
                                type="button"
                                onClick={() => void handleRagDelete(doc.id)}
                                disabled={ragActionDocId === doc.id}
                                className="rounded-lg bg-red-600 px-2 py-1 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
                              >
                                Delete
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
          )}

          {activeTab === 'access-requests' && (
            <section className="mb-12">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-medium text-[var(--docs-text)]">
                    Pending access requests
                  </h2>
                  <p className="text-sm text-[var(--docs-muted)]">
                    Review and approve/reject user access from this admin tab.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void loadAccessRequests()}
                  disabled={accessLoading}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {accessLoading ? 'Refreshing…' : 'Refresh'}
                </button>
              </div>
              <div className="docs-path mb-3 inline-block">
                GET /api/admin/access-requests?status=PENDING · POST /api/admin/access-requests/:id/approve
              </div>
              {accessError && (
                <p className="mb-2 text-sm text-red-600" role="alert">
                  {accessError}
                </p>
              )}
              <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                {accessLoading ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">Loading pending requests…</div>
                ) : accessRequests.length === 0 ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">No pending access requests.</div>
                ) : (
                  <table className="min-w-full divide-y divide-[var(--docs-border)]">
                    <thead className="bg-[var(--docs-code-bg)]">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">User</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Requested</th>
                        <th className="px-4 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--docs-border)]">
                      {accessRequests.map((request) => (
                        <tr key={request.id}>
                          <td className="px-4 py-3 text-sm">
                            <p className="font-medium text-[var(--docs-text)]">
                              {request.displayName || request.email}
                            </p>
                            <p className="text-xs text-[var(--docs-muted)]">{request.email}</p>
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {formatDate(request.createdAt)}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => void handleApprove(request.id)}
                                disabled={processingRequestId === request.id}
                                className="rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                onClick={() => void handleReject(request.id)}
                                disabled={processingRequestId === request.id}
                                className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
                              >
                                Reject
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
          )}

          {activeTab === 'users' && (
            <section className="mb-12">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-medium text-[var(--docs-text)]">
                    Users
                  </h2>
                  <p className="text-sm text-[var(--docs-muted)]">
                    View and remove registered users. Deleting a user cannot be undone.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void loadUsers()}
                  disabled={usersLoading}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
                >
                  {usersLoading ? 'Refreshing…' : 'Refresh'}
                </button>
              </div>
              <div className="docs-path mb-3 inline-block">
                GET /api/admin/users · POST /api/admin/users · PUT /api/admin/users/:id · DELETE /api/admin/users/:id
              </div>
              <div className="mb-4 rounded-lg border border-[var(--docs-border)] bg-white p-4">
                <h3 className="mb-3 text-sm font-medium text-[var(--docs-text)]">
                  {userForm.id ? 'Edit user' : 'Add user'}
                </h3>
                <div className="grid gap-3 md:grid-cols-2">
                  <label className="text-sm text-[var(--docs-text)]">
                    Email
                    <input
                      type="email"
                      value={userForm.email}
                      onChange={(e) => setUserForm((prev) => ({ ...prev, email: e.target.value }))}
                      className="mt-1 w-full rounded border border-[var(--docs-border)] px-3 py-2 text-sm"
                      placeholder="user@example.com"
                    />
                  </label>
                  <label className="text-sm text-[var(--docs-text)]">
                    Username
                    <input
                      type="text"
                      value={userForm.username}
                      onChange={(e) => setUserForm((prev) => ({ ...prev, username: e.target.value }))}
                      className="mt-1 w-full rounded border border-[var(--docs-border)] px-3 py-2 text-sm"
                      placeholder="optional (auto from email)"
                    />
                  </label>
                  <label className="text-sm text-[var(--docs-text)]">
                    Display name
                    <input
                      type="text"
                      value={userForm.displayName}
                      onChange={(e) => setUserForm((prev) => ({ ...prev, displayName: e.target.value }))}
                      className="mt-1 w-full rounded border border-[var(--docs-border)] px-3 py-2 text-sm"
                    />
                  </label>
                  <label className="text-sm text-[var(--docs-text)]">
                    Role
                    <select
                      value={userForm.role}
                      onChange={(e) =>
                        setUserForm((prev) => ({
                          ...prev,
                          role: e.target.value as 'ADMIN' | 'BASIC' | 'PREMIUM',
                        }))
                      }
                      className="mt-1 w-full rounded border border-[var(--docs-border)] px-3 py-2 text-sm"
                    >
                      <option value="BASIC">BASIC</option>
                      <option value="PREMIUM">PREMIUM</option>
                      <option value="ADMIN">ADMIN</option>
                    </select>
                  </label>
                  <label className="flex items-center gap-2 text-sm text-[var(--docs-text)] md:col-span-2">
                    <input
                      type="checkbox"
                      checked={userForm.registered}
                      onChange={(e) =>
                        setUserForm((prev) => ({ ...prev, registered: e.target.checked }))
                      }
                      className="h-4 w-4 rounded border-[var(--docs-border)]"
                    />
                    Mark as fully registered
                  </label>
                </div>
                <div className="mt-3 flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void handleSaveUser()}
                    disabled={savingUser}
                    className="rounded-lg bg-[var(--docs-accent)] px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
                  >
                    {savingUser
                      ? userForm.id
                        ? 'Saving…'
                        : 'Adding…'
                      : userForm.id
                        ? 'Save changes'
                        : 'Add user'}
                  </button>
                  {userForm.id && (
                    <button
                      type="button"
                      onClick={resetUserForm}
                      className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                    >
                      Cancel edit
                    </button>
                  )}
                </div>
              </div>
              {usersError && (
                <p className="mb-2 text-sm text-red-600" role="alert">
                  {usersError}
                </p>
              )}
              <div className="overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
                {usersLoading && users.length === 0 ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">Loading users…</div>
                ) : users.length === 0 ? (
                  <div className="p-4 text-sm text-[var(--docs-muted)]">No users.</div>
                ) : (
                  <table className="min-w-full divide-y divide-[var(--docs-border)]">
                    <thead className="bg-[var(--docs-code-bg)]">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">User</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Role</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Registered</th>
                        <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Last login</th>
                        <th className="px-4 py-2 text-right text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--docs-border)]">
                      {users.map((u) => (
                        <tr key={u.id}>
                          <td className="px-4 py-3 text-sm">
                            <p className="font-medium text-[var(--docs-text)]">
                              {u.displayName || u.username || u.email}
                            </p>
                            <p className="text-xs text-[var(--docs-muted)]">{u.email}</p>
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {u.role}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {u.registered ? 'Yes' : 'No'}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--docs-muted)]">
                            {u.lastLoginAt ? formatDate(u.lastLoginAt) : '—'}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => handleEditUser(u)}
                                className="rounded-lg border border-[var(--docs-border)] bg-white px-3 py-1.5 text-xs font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                              >
                                Edit
                              </button>
                              <button
                                type="button"
                                onClick={() => void handleDeleteUser(u.id, u.email)}
                                disabled={deletingUserId === u.id}
                                className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
                              >
                                {deletingUserId === u.id ? 'Deleting…' : 'Delete'}
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
          )}

          {activeTab === 'system' && (
            <>
        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Auth
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            Session and API key configuration (for debugging).
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/debug/auth · GET /api/me
          </div>
          <div className="rounded-lg border border-[var(--docs-border)] bg-white p-4">
            {authDebugLoading && (
              <p className="text-sm text-[var(--docs-muted)]">Loading…</p>
            )}
            {!authDebugLoading && authDebug && (
              <dl className="space-y-2 text-sm">
                <div className="flex items-center gap-2">
                  <dt className="font-medium text-[var(--docs-muted)]">
                    API key configured:
                  </dt>
                  <dd>
                    <span
                      className={
                        authDebug.apiKeyConfigured
                          ? 'text-green-600'
                          : 'text-amber-600'
                      }
                    >
                      {authDebug.apiKeyConfigured ? 'Yes' : 'No'}
                    </span>
                  </dd>
                </div>
                <div className="flex items-center gap-2">
                  <dt className="font-medium text-[var(--docs-muted)]">
                    Login debug (AUTH_DEBUG):
                  </dt>
                  <dd>
                    <span
                      className={
                        authDebug.authDebugEnabled
                          ? 'text-green-600'
                          : 'text-[var(--docs-muted)]'
                      }
                    >
                      {authDebug.authDebugEnabled ? 'On' : 'Off'}
                    </span>
                  </dd>
                </div>
                <div className="flex items-center gap-2">
                  <dt className="font-medium text-[var(--docs-muted)]">
                    Backend session:
                  </dt>
                  <dd className="font-mono text-xs">
                    {authDebug.userId ?? '—'}
                  </dd>
                </div>
                <div className="flex items-center gap-2">
                  <dt className="font-medium text-[var(--docs-muted)]">
                    Username:
                  </dt>
                  <dd>{authDebug.username ?? '—'}</dd>
                </div>
                {user && (
                        <div className="flex items-center gap-2 border-t border-[var(--docs-border)] pt-2">
                    <dt className="font-medium text-[var(--docs-muted)]">
                      Frontend (useAuth):
                    </dt>
                    <dd className="flex items-center gap-2">
                      {user.profileImageUrl && (
                        <img
                          src={user.profileImageUrl}
                          alt=""
                          className="h-8 w-8 rounded-full object-cover"
                          width={32}
                          height={32}
                        />
                      )}
                      <span>
                        {user.displayName || user.username} ({user.id})
                      </span>
                    </dd>
                  </div>
                )}
              </dl>
            )}
          </div>
        </section>

        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Personas
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            Manage expert personas used in chat (finance, legal, tax, medical,
            trusted advisor).
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/personas · POST /api/personas · PUT /api/personas/:id ·
            DELETE /api/personas/:id
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Link
              href="/personas"
              className="inline-flex items-center rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)]"
            >
              Manage personas
              <span className="ml-2">→</span>
            </Link>
            <Link
              href="/chat"
              className="inline-flex items-center rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)]"
            >
              Open chat
              <span className="ml-2">→</span>
            </Link>
          </div>
        </section>

        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Debug & xAI
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            Enable debug and verify the Grok (xAI) API connection.
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/chat/config · PUT /api/chat/config · GET /api/chat/config/test
          </div>
          {configError && (
            <p className="mb-2 text-sm text-red-600" role="alert">
              {configError}
            </p>
          )}
          <div className="space-y-4 rounded-lg border border-[var(--docs-border)] bg-white p-4">
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="checkbox"
                checked={config.debug === true}
                onChange={(e) => void setDebug(e.target.checked)}
                disabled={configLoading}
                className="h-4 w-4 rounded border-[var(--docs-border)] text-[var(--docs-accent)] focus:ring-[var(--docs-accent)]"
              />
              <span className="text-sm font-medium text-[var(--docs-text)]">
                Enable debug
              </span>
            </label>
            <div>
              <button
                type="button"
                onClick={() => void testConnection()}
                disabled={testLoading}
                className="inline-flex items-center rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm font-medium text-[var(--docs-text)] hover:border-[var(--docs-accent)] hover:bg-[var(--docs-code-bg)] disabled:opacity-50"
              >
                {testLoading ? 'Testing…' : 'Test xAI connection'}
              </button>
              {testResult && (
                <div
                  className={`mt-2 rounded p-2 text-sm ${
                    testResult.success
                      ? 'bg-green-50 text-green-800'
                      : 'bg-red-50 text-red-800'
                  }`}
                  role="status"
                >
                  {testResult.success ? '✓ ' : '✗ '}
                  {testResult.message}
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="mb-12">
          <h2 className="mb-1 text-lg font-medium text-[var(--docs-text)]">
            Prompt & context
          </h2>
          <p className="mb-3 text-sm text-[var(--docs-muted)]">
            System prompt override, risk profile, and strategy goals (when chat config API is enabled).
          </p>
          <div className="docs-path mb-3 inline-block">
            GET /api/chat/config · PUT /api/chat/config
          </div>
          <div className="rounded-lg border border-[var(--docs-border)] bg-white p-4 text-sm text-[var(--docs-muted)]">
            Chat config (tools, context.personaId, systemPromptOverride) can be
            saved per user. Use the persona selector and system prompt field in{' '}
            <Link
              href="/chat"
              className="text-[var(--docs-accent)] hover:underline"
            >
              Chat
            </Link>{' '}
            once config endpoints are wired.
          </div>
        </section>

        <section className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4">
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">
            API base
          </h3>
          <p className="docs-path text-sm">
            {origin || '[your-domain]'}/api
          </p>
          <p className="mt-2 text-xs text-[var(--docs-muted)]">
              Backend proxy: requests to /api/* are forwarded to the Spring Boot app (e.g. localhost:8080).
            </p>
          </section>
            </>
          )}
        </main>
      </div>
    </AdminGuard>
  )
}
