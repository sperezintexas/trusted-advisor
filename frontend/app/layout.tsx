import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'
import AuthGuard from './components/AuthGuard'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'Trusted Advisor',
  description: 'AI Advisor with Personas',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <AuthGuard>
          <div className="app-container">
            {children}
            <footer className="mt-auto border-t border-[var(--docs-border)] py-3 px-4 text-center text-xs text-[var(--docs-muted)]">
              v0.2.3
            </footer>
          </div>
        </AuthGuard>
      </body>
    </html>
  )
}