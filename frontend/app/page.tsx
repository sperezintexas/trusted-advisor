'use client'

import { useState, useEffect, useRef } from 'react'
import Link from 'next/link'

type ChatMessage = { role: 'user' | 'assistant'; content: string }

const EXAMPLE_PROMPTS = [
  'What should I consider for retirement planning?',
  'What are my portfolio account names?',
  'IONQ or ASTS targets for CSP 7–21 DTE',
  'Review my portfolio risk and suggest adjustments.',
  'Covered call ideas',
  'Market outlook',
]

function ConfigIcon() {
  return (
    <svg className="h-2 w-2 shrink-0 block" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

export default function Home() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [examplePromptsOpen, setExamplePromptsOpen] = useState(true) // expanded when no history
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    fetch('/api/chat/history?userId=default')
      .then((r) => r.json())
      .then((data: ChatMessage[]) => {
        const list = Array.isArray(data) ? data : []
        setMessages(list)
        if (list.length > 0) setExamplePromptsOpen(false)
      })
  }, [])

  const sendMessage = async () => {
    if (!input.trim() || loading) return
    const userMessage: ChatMessage = { role: 'user', content: input.trim() }
    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setLoading(true)
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: userMessage.content }),
      })
      const data = await res.json()
      setMessages((prev) => [...prev, { role: 'assistant', content: data.response ?? '' }])
    } catch (err) {
      setMessages((prev) => [...prev, { role: 'assistant', content: 'Error: ' + (err as Error).message }])
    }
    setLoading(false)
  }

  return (
    <div className="flex min-h-screen flex-col bg-white">
      {/* Header: Chat + gear (like reference) */}
      <header className="flex shrink-0 items-center justify-between border-b border-[var(--docs-border)] py-3">
        <h1 className="text-lg font-bold text-[var(--docs-text)]">Chat</h1>
        <Link
            href="/config"
            className="inline-flex shrink-0 items-center justify-center size-5 rounded text-[var(--docs-muted)] hover:text-[var(--docs-accent)]"
            title="Configuration"
            aria-label="Configuration"
          >
            <ConfigIcon />
          </Link>
      </header>

      {/* Chat history: scrollable white area */}
      <div className="flex-1 overflow-y-auto bg-white px-4 py-4 min-h-[240px]">
        {messages.length === 0 && !loading && (
          <div className="flex flex-col items-center justify-center py-8 text-center">
            <p className="text-sm text-[var(--docs-muted)] mb-4">No conversation yet.</p>
            <p className="text-xs text-[var(--docs-muted)]">Choose an example below or type your question.</p>
          </div>
        )}
        {messages.map((msg, i) => (
          <div
            key={i}
            className={`mb-4 ${msg.role === 'user' ? 'flex justify-end' : ''}`}
          >
            {msg.role === 'user' ? (
              <div className="rounded-2xl rounded-br-md bg-[var(--docs-accent)] px-4 py-2.5 text-white text-sm max-w-[85%]">
                {msg.content}
              </div>
            ) : (
              <div className="text-left text-sm text-[var(--docs-text)] max-w-[90%] whitespace-pre-wrap">
                {msg.content}
              </div>
            )}
          </div>
        ))}
        {loading && (
          <div className="text-sm text-[var(--docs-muted)]">Typing...</div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input area: placeholder + Send + Stats */}
      <div className="shrink-0 border-t border-[var(--docs-border)] bg-white p-4">
        <div className="flex gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), sendMessage())}
            placeholder="Smart Grok Chat — Ask about anything but focus on stocks, market outlook, portfolio, or investment strategies. Powered by xAI Grok."
            className="flex-1 rounded-lg border-2 border-gray-300 bg-white px-4 py-2.5 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] shadow-sm focus:border-[var(--docs-accent)] focus:outline-none focus:ring-2 focus:ring-[var(--docs-accent)] focus:ring-opacity-30"
            disabled={loading}
          />
          <button
            onClick={sendMessage}
            disabled={loading || !input.trim()}
            className="shrink-0 rounded-lg bg-[var(--docs-accent)] px-5 py-2.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
          >
            Send
          </button>
          <button
            type="button"
            className="shrink-0 rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2.5 text-sm font-medium text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)]"
            title="Stats"
          >
            Stats
          </button>
        </div>

        {/* Example prompts: expanded by default when no history */}
        <div className="mt-3 rounded-lg border border-[var(--docs-border)] overflow-hidden">
          <button
            type="button"
            onClick={() => setExamplePromptsOpen((o) => !o)}
            className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
          >
            <span className="text-[var(--docs-muted)]">{examplePromptsOpen ? '▼' : '►'}</span>
            Example prompts
          </button>
          {examplePromptsOpen && (
            <div className="border-t border-[var(--docs-border)] flex flex-wrap gap-2 p-3 bg-[var(--docs-bg)]">
              {EXAMPLE_PROMPTS.map((ex) => (
                <button
                  key={ex}
                  type="button"
                  onClick={() => setInput(ex)}
                  className="rounded border border-[var(--docs-border)] bg-white px-3 py-1.5 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                >
                  {ex}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
