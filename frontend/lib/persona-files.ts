import { apiUrl, defaultFetchOptions } from './api'

export type PersonaFileView = {
  id: string
  personaId: string
  sourceType: string
  sourceFileId: string
  sourceUrl: string | null
  name: string
  mimeType: string | null
  sizeBytes: number | null
  status: 'PENDING' | 'INDEXING' | 'INDEXED' | 'FAILED'
  lastError: string | null
  chunkCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type PersonaFilesResponse = {
  files: PersonaFileView[]
}

export type PersonaFileResponse = {
  success: boolean
  message: string
  file: PersonaFileView | null
}

export type AddFileRequest = {
  sourceType?: string
  sourceFileId: string
  sourceUrl?: string
  name: string
  mimeType?: string
  sizeBytes?: number
}

export async function fetchPersonaFiles(
  personaId: string
): Promise<PersonaFilesResponse | null> {
  try {
    const res = await fetch(
      apiUrl(`/personas/${personaId}/files`),
      defaultFetchOptions()
    )
    if (!res.ok) return null
    return (await res.json()) as PersonaFilesResponse
  } catch {
    return null
  }
}

export async function addPersonaFile(
  personaId: string,
  request: AddFileRequest
): Promise<PersonaFileResponse | null> {
  try {
    const res = await fetch(apiUrl(`/personas/${personaId}/files`), {
      ...defaultFetchOptions(),
      method: 'POST',
      headers: {
        ...defaultFetchOptions().headers,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    })
    if (!res.ok) return null
    return (await res.json()) as PersonaFileResponse
  } catch {
    return null
  }
}

export async function removePersonaFile(
  personaId: string,
  fileId: string
): Promise<PersonaFileResponse | null> {
  try {
    const res = await fetch(apiUrl(`/personas/${personaId}/files/${fileId}`), {
      ...defaultFetchOptions(),
      method: 'DELETE',
    })
    if (!res.ok) return null
    return (await res.json()) as PersonaFileResponse
  } catch {
    return null
  }
}

export async function indexPersonaFile(
  personaId: string,
  fileId: string,
  content: string
): Promise<PersonaFileResponse | null> {
  try {
    const res = await fetch(
      apiUrl(`/personas/${personaId}/files/${fileId}/index`),
      {
        ...defaultFetchOptions(),
        method: 'POST',
        headers: {
          ...defaultFetchOptions().headers,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ content }),
      }
    )
    if (!res.ok) return null
    return (await res.json()) as PersonaFileResponse
  } catch {
    return null
  }
}

export function formatFileSize(bytes: number | null): string {
  if (bytes === null) return 'Unknown'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function getStatusColor(status: PersonaFileView['status']): string {
  switch (status) {
    case 'INDEXED':
      return 'text-green-600 bg-green-100'
    case 'PENDING':
      return 'text-yellow-600 bg-yellow-100'
    case 'INDEXING':
      return 'text-blue-600 bg-blue-100'
    case 'FAILED':
      return 'text-red-600 bg-red-100'
    default:
      return 'text-gray-600 bg-gray-100'
  }
}
