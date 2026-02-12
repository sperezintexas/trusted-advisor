'use client'

import { Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import AppHeader from '../components/AppHeader'

type ChatRole = 'user' | 'assistant'
type ChatMessage = { role: ChatRole; content: string }
type Persona = { id: string; name: string; description: string }

const DEFAULT_USER_ID = 'default'

const EXAMPLE_PROMPTS = [
  'TSLA price',
  'Portfolio risk',
  'Covered call ideas',
  'Market outlook',
  'IONQ or ASTS targets for CSP 7–21 DTE',
]

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const errorMessage = (value: unknown): string =>
  value instanceof Error ? value.message : 'Unknown error'

const toPersonas = (value: unknown): Persona[] => {
  if (!Array.isArray(value)) return []

  return value.flatMap((item) => {
    if (!isRecord(item)) return []

    const id = typeof item.id === 'string' ? item.id.trim() : ''
    const name = typeof item.name === 'string' ? item.name.trim() : ''
    const description =
      typeof item.description === 'string' ? item.description.trim() : ''

    if (!id || !name) return []

    return [{ id, name, description }]
  })
}

const toChatMessages = (value: unknown): ChatMessage[] => {
  if (!Array.isArray(value)) return []

  return value.flatMap((item) => {
    if (!isRecord(item)) return []

    const role = item.role
    const content = item.content

    if ((role === 'user' || role === 'assistant') && typeof content === 'string') {
      return [{ role, content }]
    }

    return []
  })
}

const getResponseError = async (res: Response): Promise<string> => {
  try {
    const json = await res.json()
    if (isRecord(json) && typeof json.message === 'string' && json.message.trim()) {
      return json.message
    }
  } catch {
    // Ignore parsing errors and fallback to status-based message.
  }

  return `Request failed (${res.status})`
}

function ChatContent() {
  const searchParams = useSearchParams()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [personas, setPersonas] = useState<Persona[]>([])
  const [currentPersonaId, setCurrentPersonaId] = useState('')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingInitial, setLoadingInitial] = useState(true)
  const [initialError, setInitialError] = useState<string | null>(null)
  const [sendError, setSendError] = useState<string | null>(null)
  const [exampleOpen, setExampleOpen] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    const q = searchParams.get('q')
    if (q) {
      setInput(q)
      requestAnimationFrame(() => composerRef.current?.focus())
    }
  }, [searchParams])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const loadInitialData = useCallback(async () => {
    setLoadingInitial(true)
    setInitialError(null)

    try {
      const [personaResult, historyResult] = await Promise.allSettled([
        fetch('/api/personas'),
        fetch(`/api/chat/history?userId=${DEFAULT_USER_ID}`),
      ])

      const loadErrors: string[] = []

      if (personaResult.status === 'fulfilled') {
        if (personaResult.value.ok) {
          const data: unknown = await personaResult.value.json()
          const parsed = toPersonas(data)
          setPersonas(parsed)
          setCurrentPersonaId((previous) => {
            if (previous && parsed.some((persona) => persona.id === previous)) {
              return previous
            }
            return parsed[0]?.id ?? ''
          })
        } else {
          loadErrors.push(await getResponseError(personaResult.value))
          setPersonas([])
          setCurrentPersonaId('')
        }
      } else {
        loadErrors.push(errorMessage(personaResult.reason))
        setPersonas([])
        setCurrentPersonaId('')
      }

      if (historyResult.status === 'fulfilled') {
        if (historyResult.value.ok) {
          const data: unknown = await historyResult.value.json()
          const parsed = toChatMessages(data)
          setMessages(parsed)
          setExampleOpen(parsed.length === 0)
        } else {
          loadErrors.push(await getResponseError(historyResult.value))
          setMessages([])
          setExampleOpen(true)
        }
      } else {
        loadErrors.push(errorMessage(historyResult.reason))
        setMessages([])
        setExampleOpen(true)
      }

      if (loadErrors.length > 0) {
        setInitialError(
          `Could not fully load chat context: ${loadErrors.join(' · ')}`,
        )
      }
    } catch (err) {
      setInitialError(`Could not load chat context: ${errorMessage(err)}`)
      setPersonas([])
      setCurrentPersonaId('')
      setMessages([])
      setExampleOpen(true)
    } finally {
      setLoadingInitial(false)
    }
  }, [])

  useEffect(() => {
    void loadInitialData()
  }, [loadInitialData])

  const sendMessage = useCallback(async () => {
    if (!input.trim() || loading) return

    const userMessage: ChatMessage = { role: 'user', content: input.trim() }
    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setLoading(true)
    setSendError(null)

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: userMessage.content,
          personaId: currentPersonaId || undefined,
        }),
      })

      if (!res.ok) {
        throw new Error(await getResponseError(res))
      }

      const data: unknown = await res.json()
      const response =
        isRecord(data) && typeof data.response === 'string'
          ? data.response
          : 'No response received.'

      setMessages((prev) => [...prev, { role: 'assistant', content: response }])
    } catch (err) {
      const message = errorMessage(err)
      setSendError(message)
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `I hit an error while sending your request: ${message}`,
        },
      ])
    } finally {
      setLoading(false)
    }
  }, [currentPersonaId, input, loading])

  const clearMessages = () => {
    setMessages([])
    setExampleOpen(true)
    setSendError(null)
    composerRef.current?.focus()
  }

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <div className="mx-auto flex w-full max-w-4xl flex-1 flex-col p-4">
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-lg font-bold text-[var(--docs-text)]">Chat</h1>
            <p className="text-sm text-[var(--docs-muted)]">
              Ask questions about markets, strategy, and portfolio decisions.
            </p>
          </div>
          <div className="min-w-[220px]">
            <label
              htmlFor="persona-select"
              className="mb-1 block text-xs font-medium uppercase tracking-wide text-[var(--docs-muted)]"
            >
              Persona
            </label>
            <select
              id="persona-select"
              value={currentPersonaId}
              onChange={(e) => setCurrentPersonaId(e.target.value)}
              disabled={personas.length === 0 || loadingInitial}
              className="w-full rounded-md border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)] disabled:cursor-not-allowed disabled:bg-[var(--docs-code-bg)] disabled:text-[var(--docs-muted)]"
              aria-label="Persona"
            >
              {personas.length === 0 ? (
                <option value="">No personas available</option>
              ) : (
                personas.map((persona) => (
                  <option key={persona.id} value={persona.id}>
                    {persona.name}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>

        <section className="flex flex-1 flex-col overflow-hidden rounded-xl border border-[var(--docs-border)] bg-white shadow-sm">
          <div className="flex-1 overflow-y-auto px-4 py-4">
            {loadingInitial && (
              <p className="py-6 text-sm text-[var(--docs-muted)]" role="status">
                Loading conversation...
              </p>
            )}

            {!loadingInitial && initialError && (
              <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                <p>{initialError}</p>
                <button
                  type="button"
                  onClick={() => void loadInitialData()}
                  className="mt-2 rounded bg-white px-2.5 py-1 text-xs font-medium text-red-700 hover:bg-red-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                >
                  Retry load
                </button>
              </div>
            )}

            {!loadingInitial && messages.length === 0 && (
              <div className="py-6 text-center">
                <p className="text-sm text-[var(--docs-muted)]">
                  No messages yet. Send a prompt or pick an example below.
                </p>
              </div>
            )}

            {messages.map((msg, index) => (
              <div
                key={`${msg.role}-${index}-${msg.content.slice(0, 24)}`}
                className={`mb-3 ${msg.role === 'user' ? 'flex justify-end' : ''}`}
              >
                {msg.role === 'user' ? (
                  <div className="max-w-[85%] rounded-2xl rounded-br-md bg-[var(--docs-accent)] px-4 py-2 text-sm text-white">
                    {msg.content}
                  </div>
                ) : (
                  <div className="max-w-[90%] whitespace-pre-wrap rounded-xl border border-[var(--docs-border)] bg-[var(--docs-bg)] px-3 py-2 text-sm text-[var(--docs-text)]">
                    {msg.content}
                  </div>
                )}
              </div>
            ))}

            {loading && (
              <p className="text-sm text-[var(--docs-muted)]" role="status" aria-live="polite">
                Assistant is typing...
              </p>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div className="border-t border-[var(--docs-border)] p-4">
            {sendError && (
              <p className="mb-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
                Last message failed: {sendError}
              </p>
            )}
            <div className="flex items-start gap-2">
              <textarea
                ref={composerRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    void sendMessage()
                  }
                }}
                placeholder="Ask about stocks, portfolio, or strategy..."
                className="min-h-[84px] flex-1 resize-y rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm text-[var(--docs-text)] placeholder:text-[var(--docs-muted)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                disabled={loading || loadingInitial}
                aria-label="Message"
              />
              <div className="flex shrink-0 flex-col gap-2">
                <button
                  onClick={() => void sendMessage()}
                  disabled={loading || loadingInitial || !input.trim()}
                  className="rounded-lg bg-[var(--docs-accent)] px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Send
                </button>
                <button
                  type="button"
                  onClick={clearMessages}
                  disabled={loading || loadingInitial || messages.length === 0}
                  className="rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Clear view
                </button>
              </div>
            </div>
            <p className="mt-2 text-xs text-[var(--docs-muted)]">
              Press Enter to send, Shift + Enter for a new line.
            </p>

            <div className="mt-3 overflow-hidden rounded-lg border border-[var(--docs-border)] bg-white">
              <button
                type="button"
                onClick={() => setExampleOpen((open) => !open)}
                className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm font-medium text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                aria-expanded={exampleOpen}
              >
                <span className="text-[var(--docs-muted)]">{exampleOpen ? '▼' : '►'}</span>
                Example prompts
              </button>
              {exampleOpen && (
                <div className="flex flex-wrap gap-2 border-t border-[var(--docs-border)] p-3">
                  {EXAMPLE_PROMPTS.map((example) => (
                    <button
                      key={example}
                      type="button"
                      onClick={() => {
                        setInput(example)
                        composerRef.current?.focus()
                      }}
                      className="rounded border border-[var(--docs-border)] px-3 py-1.5 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                    >
                      {example}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

export default function ChatPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-[var(--docs-bg)]">
          Loading...
        </div>
      }
    >
      <ChatContent />
    </Suspense>
  )
}
