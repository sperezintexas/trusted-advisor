'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { fetchAuthSession, useAuth } from '@/lib/auth'

export type { User } from '@/lib/auth'

type AuthGuardProps = {
  children: React.ReactNode
}

export default function AuthGuard({ children }: AuthGuardProps) {
  const { user, loading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const [needsRegistration, setNeedsRegistration] = useState(false)

  useEffect(() => {
    if (loading) return
    if (pathname === '/login' || pathname === '/register') return
    if (!user) {
      router.replace('/login')
      return
    }
    // User is present; check if they must complete registration.
    void fetchAuthSession().then((session) => {
      if (!session) return
      if (session.allowed && session.needsRegistration) {
        setNeedsRegistration(true)
        if (pathname !== '/register') {
          router.replace('/register')
        }
      } else {
        setNeedsRegistration(false)
      }
    })
  }, [user, loading, pathname, router])

  if (pathname === '/login' || pathname === '/register') {
    return <>{children}</>
  }
  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
        <p className="text-sm text-[var(--docs-muted)]">Loading…</p>
      </div>
    )
  }
  if (!user) {
    return null
  }
  return <>{children}</>
}
