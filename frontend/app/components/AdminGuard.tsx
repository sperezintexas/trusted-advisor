'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { fetchAuthSession, type UserRole } from '@/lib/auth'

type AdminGuardProps = {
  children: React.ReactNode
}

export default function AdminGuard({ children }: AdminGuardProps) {
  const router = useRouter()
  const [role, setRole] = useState<UserRole | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchAuthSession().then((session) => {
      if (session?.user?.role) {
        setRole(session.user.role)
        if (session.user.role !== 'ADMIN') {
          router.replace('/coach')
        }
      } else {
        router.replace('/login')
      }
      setLoading(false)
    })
  }, [router])

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
        <p className="text-sm text-[var(--docs-muted)]">Loading...</p>
      </div>
    )
  }

  if (role !== 'ADMIN') {
    return null
  }

  return <>{children}</>
}
