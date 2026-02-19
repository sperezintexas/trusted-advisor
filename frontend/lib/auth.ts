'use client'

import { usePathname } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { apiUrl, getStoredApiKey, clearStoredApiKey, clearStoredUserId, defaultFetchOptions } from './api'

const AUTH_DEBUG =
  typeof process !== 'undefined' &&
  process.env.NEXT_PUBLIC_AUTH_DEBUG === 'true'

/** True when NEXT_PUBLIC_AUTH_DEBUG=true. */
export function isAuthDebugEnabled(): boolean {
  return AUTH_DEBUG
}

/** Log auth debug message to console when NEXT_PUBLIC_AUTH_DEBUG=true. */
export function authDebugLog(message: string, detail?: unknown): void {
  if (AUTH_DEBUG) {
    if (detail !== undefined) {
      console.log('[auth]', message, detail)
    } else {
      console.log('[auth]', message)
    }
  }
}

/** GET /me status for debug (e.g. "200 OK" or "401 Unauthorized"). */
export async function checkSessionStatus(): Promise<string> {
  try {
    const res = await fetch(apiUrl('/me'), defaultFetchOptions())
    return `${res.status} ${res.statusText}`
  } catch (e) {
    return `Error: ${e instanceof Error ? e.message : String(e)}`
  }
}

export type User = {
  id: string
  username: string
  displayName: string | null
  profileImageUrl: string | null
}

/**
 * Invalidate stored API key and redirect to login. Use for logout buttons.
 */
export function logout(): void {
  clearStoredApiKey(); clearStoredUserId()
  window.location.href = '/login'
}

/**
 * Fetch current user from backend using stored API key. Returns null if unauthenticated or no key.
 */
export async function fetchSession(): Promise<User | null> {
  const key = getStoredApiKey()
  if (!key) return null
  const res = await fetch(apiUrl('/me'), defaultFetchOptions())
  if (!res.ok) return null
  const data = (await res.json()) as User
  return data
}

/**
 * Hook: current user and loading. Refetches when pathname changes.
 */
export function useAuth(): {
  user: User | null
  loading: boolean
  refetch: () => Promise<void>
} {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const pathname = usePathname()

  const refetch = useCallback(async () => {
    const data = await fetchSession()
    setUser(data)
  }, [])

  useEffect(() => {
    if (pathname === '/login') {
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    fetchSession()
      .then((data) => {
        if (!cancelled && data) setUser(data)
      })
      .catch(() => {
        if (!cancelled) setUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [pathname])

  return { user, loading, refetch }
}
