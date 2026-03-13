'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { fetchSession } from '@/lib/auth'

export default function HomePage() {
  const router = useRouter()

  useEffect(() => {
    let cancelled = false
    fetchSession()
      .then((user) => {
        if (cancelled) return
        router.replace(user ? '/coach' : '/login')
      })
      .catch(() => {
        if (cancelled) return
        router.replace('/login')
      })
    return () => {
      cancelled = true
    }
  }, [router])

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
      <p className="text-sm text-[var(--docs-muted)]">Loading…</p>
    </div>
  )
}
