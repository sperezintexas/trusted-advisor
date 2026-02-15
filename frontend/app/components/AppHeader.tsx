'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useAuth, logout } from '@/lib/auth'

type NavItem = {
  href: string
  label: string
}

const NAV_ITEMS: NavItem[] = [
  { href: '/chat', label: 'Chat' },
  { href: '/personas', label: 'Personas' },
  { href: '/coach', label: 'Coach' },
]

export default function AppHeader() {
  const pathname = usePathname()
  const { user } = useAuth()

  const navItemClass = (isActive: boolean) =>
    [
      'inline-flex items-center rounded-md px-2.5 py-1.5 text-sm transition-colors',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2',
      isActive
        ? 'bg-[var(--docs-code-bg)] text-[var(--docs-text)]'
        : 'text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)] hover:text-[var(--docs-accent)]',
    ].join(' ')

  return (
    <header className="sticky top-0 z-10 border-b border-[var(--docs-border)] bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-4xl items-center justify-between gap-2 px-4">
        <Link
          href="/chat"
          className="truncate font-semibold text-[var(--docs-text)] transition-opacity hover:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] focus-visible:ring-offset-2"
        >
          Trusted Advisor
        </Link>
        <nav className="flex items-center gap-4">
          {NAV_ITEMS.map((item) => {
            const isActive =
              pathname === item.href ||
              (item.href !== '/' && pathname.startsWith(`${item.href}/`))

            return (
              <Link
                key={item.href}
                href={item.href}
                className={navItemClass(isActive)}
                aria-current={isActive ? 'page' : undefined}
              >
                {item.label}
              </Link>
            )
          })}
          <Link
            href="/config"
            className={navItemClass(pathname === '/config')}
            title="Prompt & persona configuration"
            aria-label="Configuration"
            aria-current={pathname === '/config' ? 'page' : undefined}
          >
            <ConfigIcon />
            <span className="ml-1.5 hidden sm:inline">Config</span>
          </Link>
          {user && (
            <span className="flex items-center gap-2 border-l border-[var(--docs-border)] pl-4">
              {user.profileImageUrl ? (
                <img
                  src={user.profileImageUrl}
                  alt=""
                  className="h-7 w-7 rounded-full object-cover"
                  width={28}
                  height={28}
                />
              ) : null}
              <span className="text-xs text-[var(--docs-muted)]" title={user.id}>
                {user.displayName || user.username}
              </span>
              <button
                type="button"
                onClick={() => void logout()}
                className="text-sm text-[var(--docs-muted)] hover:text-[var(--docs-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
              >
                Log out
              </button>
            </span>
          )}
        </nav>
      </div>
    </header>
  )
}

function ConfigIcon() {
  return (
    <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
      />
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
      />
    </svg>
  )
}
