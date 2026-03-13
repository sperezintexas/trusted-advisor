import { apiUrl, defaultFetchOptions } from './api'

export type AccessRequestView = {
  id: string
  email: string
  displayName: string | null
  reason: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  oauthProvider: string | null
  profileImageUrl: string | null
  reviewedBy: string | null
  reviewNote: string | null
  createdAt: string
  reviewedAt: string | null
}

export type AccessRequestListResponse = {
  requests: AccessRequestView[]
  total: number
}

export type AdminActionResponse = {
  success: boolean
  message: string
  request: AccessRequestView | null
}

export type UserView = {
  id: string
  email: string
  username: string
  displayName: string | null
  registered: boolean
  createdAt: string
  lastLoginAt: string | null
}

export type UserListResponse = {
  users: UserView[]
  total: number
}

export async function fetchAccessRequests(
  status?: string
): Promise<AccessRequestListResponse | null> {
  try {
    const url = status
      ? apiUrl(`/admin/access-requests?status=${status}`)
      : apiUrl('/admin/access-requests')
    const res = await fetch(url, defaultFetchOptions())
    if (!res.ok) {
      if (res.status === 403) {
        console.warn('[admin] Access denied - not an admin')
      }
      return null
    }
    return (await res.json()) as AccessRequestListResponse
  } catch {
    return null
  }
}

export async function approveAccessRequest(
  id: string,
  note?: string
): Promise<AdminActionResponse | null> {
  try {
    const res = await fetch(apiUrl(`/admin/access-requests/${id}/approve`), {
      ...defaultFetchOptions(),
      method: 'POST',
      headers: {
        ...defaultFetchOptions().headers,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ note }),
    })
    if (!res.ok) return null
    return (await res.json()) as AdminActionResponse
  } catch {
    return null
  }
}

export async function rejectAccessRequest(
  id: string,
  note?: string
): Promise<AdminActionResponse | null> {
  try {
    const res = await fetch(apiUrl(`/admin/access-requests/${id}/reject`), {
      ...defaultFetchOptions(),
      method: 'POST',
      headers: {
        ...defaultFetchOptions().headers,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ note }),
    })
    if (!res.ok) return null
    return (await res.json()) as AdminActionResponse
  } catch {
    return null
  }
}

export async function fetchUsers(): Promise<UserListResponse | null> {
  try {
    const res = await fetch(apiUrl('/admin/users'), defaultFetchOptions())
    if (!res.ok) return null
    return (await res.json()) as UserListResponse
  } catch {
    return null
  }
}

export async function deleteUser(id: string): Promise<AdminActionResponse | null> {
  try {
    const res = await fetch(apiUrl(`/admin/users/${id}`), {
      ...defaultFetchOptions(),
      method: 'DELETE',
    })
    if (!res.ok) return null
    return (await res.json()) as AdminActionResponse
  } catch {
    return null
  }
}

// RAG persona documents (admin)
export type AdminDocumentView = {
  id: string
  personaId: string
  name: string
  status: string
  lastError: string | null
  chunkCount: number
  sizeBytes: number | null
  createdAt: string
  updatedAt: string
}

export type AdminDocumentListResponse = {
  documents: AdminDocumentView[]
}

export type AdminDocumentResponse = {
  success: boolean
  message: string
  document: AdminDocumentView | null
}

export async function fetchPersonaDocuments(
  personaId: string
): Promise<AdminDocumentListResponse | null> {
  try {
    const res = await fetch(
      apiUrl(`/admin/personas/${personaId}/documents`),
      defaultFetchOptions()
    )
    if (!res.ok) return null
    return (await res.json()) as AdminDocumentListResponse
  } catch {
    return null
  }
}

export async function uploadPersonaDocument(
  personaId: string,
  file: File
): Promise<AdminDocumentResponse | null> {
  try {
    const formData = new FormData()
    formData.set('file', file)
    const opts = defaultFetchOptions()
    const headers = new Headers(opts.headers as HeadersInit)
    headers.delete('Content-Type')
    const res = await fetch(apiUrl(`/admin/personas/${personaId}/documents`), {
      ...opts,
      method: 'POST',
      headers,
      body: formData,
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      return {
        success: false,
        message: (body as { message?: string }).message ?? `Upload failed: ${res.status}`,
        document: null,
      }
    }
    return (await res.json()) as AdminDocumentResponse
  } catch (e) {
    return {
      success: false,
      message: e instanceof Error ? e.message : 'Upload failed',
      document: null,
    }
  }
}

export async function reindexPersonaDocument(
  personaId: string,
  docId: string,
  content: string
): Promise<AdminDocumentResponse | null> {
  try {
    const res = await fetch(
      apiUrl(`/admin/personas/${personaId}/documents/${docId}/index`),
      {
        ...defaultFetchOptions(),
        method: 'POST',
        body: JSON.stringify({ content }),
      }
    )
    if (!res.ok) return null
    return (await res.json()) as AdminDocumentResponse
  } catch {
    return null
  }
}

export async function deletePersonaDocument(
  personaId: string,
  docId: string
): Promise<AdminDocumentResponse | null> {
  try {
    const res = await fetch(
      apiUrl(`/admin/personas/${personaId}/documents/${docId}`),
      {
        ...defaultFetchOptions(),
        method: 'DELETE',
      }
    )
    if (!res.ok) return null
    return (await res.json()) as AdminDocumentResponse
  } catch {
    return null
  }
}

// Generate questions from persona RAG documents
export type GeneratedChoiceView = {
  letter: string
  text: string
}

export type GeneratedQuestionView = {
  question: string
  choices: GeneratedChoiceView[]
  correctLetter: string
  explanation: string
  topic?: string | null
}

export type GenerateQuestionsResponse = {
  success: boolean
  message: string
  questions: GeneratedQuestionView[]
}

export async function generatePersonaQuestions(
  personaId: string,
  count: number,
  options?: { examCode?: string; saveToPool?: boolean }
): Promise<GenerateQuestionsResponse | null> {
  try {
    const res = await fetch(
      apiUrl(`/admin/personas/${personaId}/generate-questions`),
      {
        ...defaultFetchOptions(),
        method: 'POST',
        body: JSON.stringify({
          count,
          examCode: options?.examCode ?? undefined,
          saveToPool: options?.saveToPool ?? undefined,
        }),
      }
    )
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      return {
        success: false,
        message: (data as { message?: string }).message ?? `Request failed: ${res.status}`,
        questions: [],
      }
    }
    return (await res.json()) as GenerateQuestionsResponse
  } catch (e) {
    return {
      success: false,
      message: e instanceof Error ? e.message : 'Request failed',
      questions: [],
    }
  }
}
