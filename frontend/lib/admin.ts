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
