'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import AppHeader from '../components/AppHeader'
import { apiUrl, defaultFetchOptions } from '@/lib/api'
import {
  createSubscriptionCheckout,
  fetchAuthSession,
  fetchSubscriptionPlans,
  verifySubscriptionCheckout,
  type AuthSession,
  type SubscriptionPlanView,
} from '@/lib/auth'

type Tier = 'BASIC' | 'PREMIUM'

export default function RegisterPage() {
  const searchParams = useSearchParams()
  const [session, setSession] = useState<AuthSession | null>(null)
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [tier, setTier] = useState<Tier>('BASIC')
  const [plans, setPlans] = useState<SubscriptionPlanView[]>([])
  const [plansLoading, setPlansLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const checkoutHandledRef = useRef(false)

  useEffect(() => {
    void fetchAuthSession().then((s) => {
      setSession(s)
      const u = s?.user
      if (u) {
        setUsername((prev) => prev || u.username)
        setDisplayName((prev) => prev || u.displayName || u.username)
      }
    })
  }, [])

  useEffect(() => {
    void fetchSubscriptionPlans().then((response) => {
      const available = response?.plans ?? []
      const defaults: (SubscriptionPlanView & { tier: Tier })[] = [
        {
          tier: 'BASIC',
          displayName: 'Basic',
          monthlyPriceUsd: '0.00',
          features: ['Exam Coach access', 'Practice exams', 'Basic chat'],
          source: 'static',
        },
        {
          tier: 'PREMIUM',
          displayName: 'Premium',
          monthlyPriceUsd: '9.99',
          features: ['Everything in Basic', 'AI Tutor sessions', 'Priority support'],
          source: 'static',
        },
      ]
      const filtered = available.filter(
        (p): p is SubscriptionPlanView & { tier: Tier } =>
          p.tier === 'BASIC' || p.tier === 'PREMIUM'
      )
      // Always show both tiers in UI; Stripe values override defaults when present.
      const mergedByTier: Record<Tier, SubscriptionPlanView & { tier: Tier }> = {
        BASIC: defaults[0],
        PREMIUM: defaults[1],
      }
      defaults.forEach((plan) => {
        mergedByTier[plan.tier] = plan
      })
      filtered.forEach((plan) => {
        mergedByTier[plan.tier] = plan
      })
      const merged = (['BASIC', 'PREMIUM'] as Tier[]).map((tierKey) => mergedByTier[tierKey])
      setPlans(merged)
      setTier((prev) => (merged.some((p) => p.tier === prev) ? prev : merged[0].tier))
      setPlansLoading(false)
    })
  }, [])

  const completeRegistration = useCallback(async (
    selectedTier: Tier,
    profile?: { username: string; displayName: string }
  ) => {
    setSubmitting(true)
    setError(null)
    try {
      const res = await fetch(
        apiUrl('/auth/register'),
        defaultFetchOptions({
          method: 'POST',
          body: JSON.stringify({
            username: profile?.username ?? username,
            displayName: profile?.displayName ?? displayName,
            tier: selectedTier,
          }),
        }),
      )
      if (!res.ok) {
        setError('Could not complete registration. Try again.')
        return
      }
      window.location.href = '/coach'
    } catch {
      setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }, [username, displayName])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!session?.allowed) return
    if (tier === 'PREMIUM') {
      setSubmitting(true)
      setError(null)
      try {
        const pendingProfile = { username, displayName }
        sessionStorage.setItem('pendingRegistrationProfile', JSON.stringify(pendingProfile))
        const checkout = await createSubscriptionCheckout({
          tier: 'PREMIUM',
          username,
          displayName,
        })
        if (!checkout?.success || !checkout.checkoutUrl) {
          setError(checkout?.message ?? 'Could not start checkout. Verify Stripe setup.')
          setSubmitting(false)
          return
        }
        window.location.href = checkout.checkoutUrl
        return
      } catch {
        setError('Could not start checkout. Try again.')
      } finally {
        setSubmitting(false)
      }
      return
    }
    await completeRegistration('BASIC')
  }

  useEffect(() => {
    const checkoutState = searchParams.get('checkout')
    const sessionId = searchParams.get('session_id')
    if (!session?.allowed) return
    if (checkoutHandledRef.current) return

    if (checkoutState === 'cancelled') {
      checkoutHandledRef.current = true
      setError('Checkout was cancelled. You can try again.')
      return
    }
    if (checkoutState !== 'success' || !sessionId) return

    checkoutHandledRef.current = true
    setSubmitting(true)
    setError(null)

    void (async () => {
      const verify = await verifySubscriptionCheckout(sessionId)
      if (!verify?.verified) {
        setError(verify?.message ?? 'Unable to verify checkout session.')
        setSubmitting(false)
        return
      }
      const stored = sessionStorage.getItem('pendingRegistrationProfile')
      let profile: { username: string; displayName: string } | undefined
      if (stored) {
        try {
          const parsed = JSON.parse(stored) as { username?: string; displayName?: string }
          profile = {
            username: parsed.username ?? username,
            displayName: parsed.displayName ?? displayName,
          }
        } catch {
          profile = { username, displayName }
        }
      }
      sessionStorage.removeItem('pendingRegistrationProfile')
      await completeRegistration('PREMIUM', profile)
    })()
  }, [searchParams, session?.allowed, username, displayName, completeRegistration])

  const email = session?.user?.email ?? ''

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="mx-auto flex min-h-[80vh] flex-1 flex-col items-center justify-center px-4 py-12">
        <div className="w-full max-w-md rounded-lg border border-[var(--docs-border)] bg-white p-8 shadow-sm">
          <h1 className="text-xl font-semibold text-[var(--docs-text)]">
            Complete your profile
          </h1>
          <p className="mt-2 text-sm text-[var(--docs-muted)]">
            You&apos;ve been authorized to use Trusted Advisor. Confirm your details
            and choose your plan.
          </p>
          <div className="mt-4 rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] px-3 py-2 text-xs text-[var(--docs-muted)]">
            {plansLoading
              ? 'Loading plan rates...'
              : `Plan rates: ${plans.map((p) => `${p.displayName} $${p.monthlyPriceUsd}/mo`).join(' · ')}`}
          </div>
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]">
                Email
              </label>
              <div className="mt-1 rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] px-3 py-2 text-sm text-[var(--docs-text)]">
                {email || 'Loading…'}
              </div>
            </div>
            <div>
              <label
                htmlFor="username"
                className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]"
              >
                Username
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
                disabled={submitting}
              />
            </div>
            <div>
              <label
                htmlFor="displayName"
                className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]"
              >
                Display name
              </label>
              <input
                id="displayName"
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
                disabled={submitting}
              />
            </div>

            <div>
              <label className="block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)] mb-3">
                Choose your plan
              </label>
              <div className="grid grid-cols-2 gap-3">
                {plans.map((plan) => (
                  <button
                    key={plan.tier}
                    type="button"
                    onClick={() => setTier(plan.tier as Tier)}
                    className={`rounded-lg border-2 p-4 text-left transition-all ${
                      tier === plan.tier
                        ? 'border-[var(--docs-accent)] bg-blue-50'
                        : 'border-[var(--docs-border)] hover:border-gray-300'
                    }`}
                  >
                    <div className="text-sm font-semibold text-[var(--docs-text)]">
                      {plan.displayName}
                    </div>
                    <div className="text-xs text-[var(--docs-muted)] mt-1">
                      ${plan.monthlyPriceUsd} / month
                    </div>
                    <ul className="mt-2 text-xs text-[var(--docs-muted)] space-y-1">
                      {plan.features.map((feature) => (
                        <li key={feature}>• {feature}</li>
                      ))}
                    </ul>
                  </button>
                ))}
              </div>
            </div>

            {error && (
              <p className="text-sm text-red-600" role="alert">
                {error}
              </p>
            )}
            <button
              type="submit"
              disabled={submitting || !session?.allowed}
              className="mt-2 w-full rounded-lg bg-black px-4 py-3 text-sm font-medium text-white hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2 disabled:opacity-50"
            >
              {submitting ? 'Processing…' : tier === 'PREMIUM' ? 'Continue to Stripe Checkout' : 'Start for free'}
            </button>
          </form>
        </div>
      </main>
    </div>
  )
}

