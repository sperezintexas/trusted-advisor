'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { fetchAuthSession, useAuth } from '@/lib/auth'

export type { User } from '@/lib/auth'

type AuthGuardProps = {
  children: React.ReactNode
}

const PUBLIC_PATHS = ['/login', '/register', '/request-access']
const ADMIN_PATHS = ['/admin']

export default function AuthGuard({ children }: AuthGuardProps) {
  const { user, loading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const [needsRegistration, setNeedsRegistration] = useState(false)
  const [checked, setChecked] = useState(false)

  const isPublicPath = PUBLIC_PATHS.some(
    (p) => pathname === p || pathname.startsWith(p + '/')
  )
  const isAdminPath = ADMIN_PATHS.some(
    (p) => pathname === p || pathname.startsWith(p + '/')
  )

  useEffect(() => {
    if (loading) return
    if (pathname === '/login') {
      setChecked(true)
      return
    }

    if (!user) {
      router.replace('/login')
      return
    }

    void fetchAuthSession().then((session) => {
      if (!session) {
        setChecked(true)
        return
      }

      if (!session.allowed) {
        if (pathname !== '/request-access') {
          router.replace('/request-access')
        }
        setChecked(true)
        return
      }

      if (session.allowed && session.needsRegistration) {
        setNeedsRegistration(true)
        if (pathname !== '/register') {
          router.replace('/register')
        }
        setChecked(true)
        return
      }

      setNeedsRegistration(false)
      setChecked(true)
    })
  }, [user, loading, pathname, router])

  if (pathname === '/login') {
    return <>{children}</>
  }

  if (loading || !checked) {
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
