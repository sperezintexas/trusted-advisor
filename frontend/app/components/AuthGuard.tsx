'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect } from 'react'
import { useAuth } from '@/lib/auth'

export type { User } from '@/lib/auth'

type AuthGuardProps = {
  children: React.ReactNode
}

export default function AuthGuard({ children }: AuthGuardProps) {
  const { user, loading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (loading || pathname === '/login') return
    if (!user) {
      router.replace('/login')
    }
  }, [user, loading, pathname, router])

  if (pathname === '/login') {
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
