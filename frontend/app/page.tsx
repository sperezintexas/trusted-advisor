'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { getStoredApiKey } from '@/lib/api'

export default function HomePage() {
  const router = useRouter()

  useEffect(() => {
    const key = getStoredApiKey()
    router.replace(key ? '/chat' : '/login')
  }, [router])

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
      <p className="text-sm text-[var(--docs-muted)]">Loading…</p>
    </div>
  )
}
