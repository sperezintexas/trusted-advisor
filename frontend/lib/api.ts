const BACKEND_ORIGIN =
  typeof process !== 'undefined'
    ? process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080'
    : 'http://localhost:8080'

const API_KEY_STORAGE_KEY = 'trusted_advisor_api_key'

/**
 * Backend origin (no path).
 */
export function getBackendUrl(): string {
  return BACKEND_ORIGIN
}

/**
 * Base URL for API calls.
 */
export function getApiBaseUrl(): string {
  return `${BACKEND_ORIGIN}/api`
}

export function apiUrl(path: string): string {
  const base = getApiBaseUrl()
  const p = path.startsWith('/') ? path : `/${path}`
  return `${base}${p}`
}

/**
 * Get the API key from localStorage (client-only). Used to send X-API-Key header.
 * localStorage survives full-page reloads and avoids client-nav timing issues.
 */
export function getStoredApiKey(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(API_KEY_STORAGE_KEY)
}

/**
 * Store the API key in localStorage. Call after "login" with the secret.
 */
export function setStoredApiKey(key: string): void {
  if (typeof window === 'undefined') return
  localStorage.setItem(API_KEY_STORAGE_KEY, key)
}

/**
 * Remove the stored API key. Call on logout.
 */
export function clearStoredApiKey(): void {
  if (typeof window === 'undefined') return
  localStorage.removeItem(API_KEY_STORAGE_KEY)
}

/**
 * Default fetch options: credentials include, JSON content type, and X-API-Key header when key is stored.
 */
export function defaultFetchOptions(init?: RequestInit): RequestInit {
  const key = typeof window !== 'undefined' ? getStoredApiKey() : null
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')
  if (key) headers.set('X-API-Key', key)
  return {
    ...init,
    credentials: 'include',
    headers,
  }
}
