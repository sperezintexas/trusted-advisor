'use client'

import { useState, useEffect, useRef, Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import AppHeader from '../components/AppHeader'

type ChatMessage = { role: 'user' | 'assistant'; content: string }
type Persona = { id: string; name: string; description: string }

const EXAMPLE_PROMPTS = [
  'TSLA price',
  'Portfolio risk',
  'Covered call ideas',
  'Market outlook',
  'IONQ or ASTS targets for CSP 7–21 DTE',
]

function ChatContent() {
  const searchParams = useSearchParams()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [personas, setPersonas] = useState<Persona[]>([])
  const [currentPersonaId, setCurrentPersonaId] = useState('')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [exampleOpen, setExampleOpen] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const q = searchParams.get('q')
    if (q) setInput(decodeURIComponent(q))
  }, [searchParams])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    fetch('/api/personas')
      .then((r) => r.json())
      .then((data: Persona[]) => {
        setPersonas(Array.isArray(data) ? data : [])
        if (data?.length) setCurrentPersonaId(data[0].id)
      })
    fetch('/api/chat/history?userId=default')
      .then((r) => r.json())
      .then((data: ChatMessage[]) => setMessages(Array.isArray(data) ? data : []))
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
        body: JSON.stringify({
          message: userMessage.content,
          personaId: currentPersonaId || undefined,
        }),
      })
      const data = await res.json()
      setMessages((prev) => [...prev, { role: 'assistant', content: data.response ?? '' }])
    } catch (err) {
      setMessages((prev) => [...prev, { role: 'assistant', content: 'Error: ' + (err as Error).message }])
    }
    setLoading(false)
  }

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <div className="flex flex-1 flex-col max-w-4xl mx-auto w-full p-4">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h1 className="text-lg font-bold text-[var(--docs-text)]">Chat</h1>
          <div className="flex items-center gap-2">
            <select
              value={currentPersonaId}
              onChange={(e) => setCurrentPersonaId(e.target.value)}
              className="rounded border border-[var(--docs-border)] bg-white px-2 py-1.5 text-sm text-[var(--docs-text)]"
              aria-label="Persona"
            >
              {personas.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
            <a
              href="/config"
              className="rounded p-1 text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)] hover:text-[var(--docs-accent)]"
              title="Configuration"
              aria-label="Configuration"
            >
              <ConfigIcon />
            </a>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto min-h-[200px] mb-4">
          {messages.length === 0 && !loading && (
            <p className="text-sm text-[var(--docs-muted)] py-4">No messages yet. Send a prompt or pick an example below.</p>
          )}
          {messages.map((msg, i) => (
            <div
              key={i}
              className={`mb-3 ${msg.role === 'user' ? 'flex justify-end' : ''}`}
            >
              {msg.role === 'user' ? (
                <div className="rounded-2xl rounded-br-md bg-[var(--docs-accent)] px-4 py-2 text-white text-sm max-w-[85%]">
                  {msg.content}
                </div>
              ) : (
                <div className="text-sm text-[var(--docs-text)] max-w-[90%] whitespace-pre-wrap">
                  {msg.content}
                </div>
              )}
            </div>
          ))}
          {loading && <p className="text-sm text-[var(--docs-muted)]">Typing…</p>}
          <div ref={messagesEndRef} />
        </div>

        <div className="flex gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), sendMessage())}
            placeholder="Ask about stocks, portfolio, or strategies. Powered by Grok."
            className="flex-1 rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2.5 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus:border-[var(--docs-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--docs-accent)]"
            disabled={loading}
            aria-label="Message"
          />
          <button
            onClick={sendMessage}
            disabled={loading || !input.trim()}
            className="shrink-0 rounded-lg bg-[var(--docs-accent)] px-5 py-2.5 font-medium text-white hover:opacity-90 disabled:opacity-50"
          >
            Send
          </button>
        </div>

        <div className="mt-3 rounded-lg border border-[var(--docs-border)] bg-white overflow-hidden">
          <button
            type="button"
            onClick={() => setExampleOpen((o) => !o)}
            className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
          >
            <span className="text-[var(--docs-muted)]">{exampleOpen ? '▼' : '►'}</span>
            Example prompts
          </button>
          {exampleOpen && (
            <div className="border-t border-[var(--docs-border)] flex flex-wrap gap-2 p-3">
              {EXAMPLE_PROMPTS.map((ex) => (
                <button
                  key={ex}
                  type="button"
                  onClick={() => setInput(ex)}
                  className="rounded border border-[var(--docs-border)] px-3 py-1.5 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
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

function ConfigIcon() {
  return (
    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

export default function ChatPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">Loading…</div>}>
      <ChatContent />
    </Suspense>
  )
}
