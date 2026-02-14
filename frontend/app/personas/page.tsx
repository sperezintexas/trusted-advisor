'use client'

import { useCallback, useEffect, useState } from 'react'
import AppHeader from '../components/AppHeader'

type Persona = {
  id: string
  name: string
  description: string
  systemPrompt: string
  webSearchEnabled: boolean
  yahooFinanceEnabled: boolean
}

type PersonaForm = {
  name: string
  description: string
  systemPrompt: string
  webSearchEnabled: boolean
  yahooFinanceEnabled: boolean
}

const EMPTY_FORM: PersonaForm = {
  name: '',
  description: '',
  systemPrompt: '',
  webSearchEnabled: true,
  yahooFinanceEnabled: true,
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const toPersonas = (value: unknown): Persona[] => {
  if (!Array.isArray(value)) return []

  return value.flatMap((item) => {
    if (!isRecord(item)) return []

    const id = typeof item.id === 'string' ? item.id.trim() : ''
    const name = typeof item.name === 'string' ? item.name.trim() : ''
    const description =
      typeof item.description === 'string' ? item.description : ''
    const systemPrompt =
      typeof item.systemPrompt === 'string' ? item.systemPrompt : ''
    const webSearchEnabled = item.webSearchEnabled !== false
    const yahooFinanceEnabled = item.yahooFinanceEnabled !== false

    if (!id || !name) return []

    return [{ id, name, description, systemPrompt, webSearchEnabled, yahooFinanceEnabled }]
  })
}

const errorMessage = (value: unknown): string =>
  value instanceof Error ? value.message : 'Unknown error'

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

export default function PersonasPage() {
  const [personas, setPersonas] = useState<Persona[]>([])
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<PersonaForm>(EMPTY_FORM)
  const [loadingList, setLoadingList] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const loadPersonas = useCallback(async () => {
    setLoadingList(true)
    setError(null)

    try {
      const res = await fetch('/api/personas')
      if (!res.ok) {
        throw new Error(await getResponseError(res))
      }

      const data: unknown = await res.json()
      setPersonas(toPersonas(data))
    } catch (err) {
      setError(`Could not load personas: ${errorMessage(err)}`)
      setPersonas([])
    } finally {
      setLoadingList(false)
    }
  }, [])

  useEffect(() => {
    void loadPersonas()
  }, [loadPersonas])

  const updateForm = <K extends keyof PersonaForm>(field: K, value: PersonaForm[K]) => {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  const savePersona = async (e: React.FormEvent) => {
    e.preventDefault()
    if (saving) return

    const payload: PersonaForm = {
      name: form.name.trim(),
      description: form.description.trim(),
      systemPrompt: form.systemPrompt.trim(),
      webSearchEnabled: true,
      yahooFinanceEnabled: form.yahooFinanceEnabled,
    }

    if (!payload.name || !payload.description || !payload.systemPrompt) {
      setError('Name, description, and system prompt are required.')
      return
    }

    setSaving(true)
    setError(null)
    setSuccessMessage(null)

    const method = editingId ? 'PUT' : 'POST'
    const url = editingId ? `/api/personas/${editingId}` : '/api/personas'

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        throw new Error(await getResponseError(res))
      }

      setEditingId(null)
      setForm(EMPTY_FORM)
      setSuccessMessage(
        method === 'PUT' ? 'Persona updated successfully.' : 'Persona created successfully.',
      )
      await loadPersonas()
    } catch (err) {
      setError(`Could not save persona: ${errorMessage(err)}`)
    } finally {
      setSaving(false)
    }
  }

  const editPersona = (persona: Persona) => {
    setError(null)
    setSuccessMessage(null)
    setEditingId(persona.id)
    setForm({
      name: persona.name,
      description: persona.description,
      systemPrompt: persona.systemPrompt,
      webSearchEnabled: persona.webSearchEnabled,
      yahooFinanceEnabled: persona.yahooFinanceEnabled,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const cancelEditing = () => {
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  const deletePersona = async (id: string, name: string) => {
    if (deletingId) return
    if (!confirm(`Delete persona "${name}"?`)) return

    setDeletingId(id)
    setError(null)
    setSuccessMessage(null)

    try {
      const res = await fetch(`/api/personas/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        throw new Error(await getResponseError(res))
      }

      setSuccessMessage('Persona deleted successfully.')
      await loadPersonas()
      if (editingId === id) {
        cancelEditing()
      }
    } catch (err) {
      setError(`Could not delete persona: ${errorMessage(err)}`)
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 py-8">
        <h1 className="mb-2 text-2xl font-semibold text-[var(--docs-text)]">
          Manage Personas
        </h1>
        <p className="mb-6 text-sm text-[var(--docs-muted)]">
          Create and edit expert personas used in chat responses.
        </p>

        {error && (
          <p className="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
            {error}
          </p>
        )}
        {successMessage && (
          <p className="mb-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
            {successMessage}
          </p>
        )}

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-xl border border-[var(--docs-border)] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold text-[var(--docs-text)]">
              {editingId ? 'Edit Persona' : 'Create Persona'}
            </h2>
            <form onSubmit={savePersona} className="space-y-4">
              <div>
                <label
                  htmlFor="persona-name"
                  className="mb-1 block text-sm font-medium text-[var(--docs-text)]"
                >
                  Name
                </label>
                <input
                  id="persona-name"
                  placeholder="Ex: Long-term Portfolio Strategist"
                  value={form.name}
                  onChange={(e) => updateForm('name', e.target.value)}
                  className="w-full rounded-lg border border-[var(--docs-border)] px-3 py-2 text-sm text-[var(--docs-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                  maxLength={80}
                  required
                />
                <p className="mt-1 text-xs text-[var(--docs-muted)]">
                  {form.name.length}/80
                </p>
              </div>

              <div>
                <label
                  htmlFor="persona-description"
                  className="mb-1 block text-sm font-medium text-[var(--docs-text)]"
                >
                  Description
                </label>
                <textarea
                  id="persona-description"
                  placeholder="One-paragraph summary of what this persona should optimize for."
                  value={form.description}
                  onChange={(e) => updateForm('description', e.target.value)}
                  className="h-24 w-full resize-y rounded-lg border border-[var(--docs-border)] px-3 py-2 text-sm text-[var(--docs-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                  maxLength={240}
                  required
                />
                <p className="mt-1 text-xs text-[var(--docs-muted)]">
                  {form.description.length}/240
                </p>
              </div>

              <div>
                <p className="mb-2 text-sm font-medium text-[var(--docs-text)]">
                  Tools for this persona
                </p>
                <div className="flex flex-wrap gap-6">
                  <span className="flex items-center gap-2 text-sm text-[var(--docs-text)]">
                    <input
                      type="checkbox"
                      checked
                      readOnly
                      tabIndex={-1}
                      className="rounded border-[var(--docs-border)]"
                      aria-label="Web search (always on)"
                    />
                    <span>Web search</span>
                    <span className="text-xs text-[var(--docs-muted)]">(default on)</span>
                  </span>
                  <label className="flex cursor-pointer items-center gap-2">
                    <input
                      type="checkbox"
                      checked={form.yahooFinanceEnabled}
                      onChange={(e) => updateForm('yahooFinanceEnabled', e.target.checked)}
                      className="rounded border-[var(--docs-border)]"
                    />
                    <span className="text-sm text-[var(--docs-text)]">Yahoo Finance</span>
                  </label>
                </div>
                <p className="mt-1 text-xs text-[var(--docs-muted)]">
                  Web search is allowed for all personas. Optionally enable Yahoo Finance for live quotes and market data.
                </p>
              </div>

              <div>
                <label
                  htmlFor="persona-system-prompt"
                  className="mb-1 block text-sm font-medium text-[var(--docs-text)]"
                >
                  System Prompt
                </label>
                <textarea
                  id="persona-system-prompt"
                  placeholder="Full persona behavior, constraints, and style instructions."
                  value={form.systemPrompt}
                  onChange={(e) => updateForm('systemPrompt', e.target.value)}
                  className="h-56 w-full resize-y rounded-lg border border-[var(--docs-border)] px-3 py-2 font-mono text-xs text-[var(--docs-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--docs-accent)]"
                  required
                />
              </div>

              <div className="flex flex-wrap gap-2">
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-lg bg-[var(--docs-accent)] px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {saving
                    ? 'Saving...'
                    : editingId
                      ? 'Update Persona'
                      : 'Create Persona'}
                </button>

                {editingId && (
                  <button
                    type="button"
                    onClick={cancelEditing}
                    className="rounded-lg border border-[var(--docs-border)] bg-white px-4 py-2 text-sm text-[var(--docs-text)] hover:bg-[var(--docs-code-bg)]"
                  >
                    Cancel
                  </button>
                )}
              </div>
            </form>
          </section>

          <section className="rounded-xl border border-[var(--docs-border)] bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center justify-between gap-2">
              <h2 className="text-lg font-semibold text-[var(--docs-text)]">
                Existing Personas
              </h2>
              <button
                type="button"
                onClick={() => void loadPersonas()}
                className="rounded border border-[var(--docs-border)] px-2.5 py-1 text-xs text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)]"
              >
                Refresh
              </button>
            </div>

            {loadingList ? (
              <p className="text-sm text-[var(--docs-muted)]">Loading personas...</p>
            ) : personas.length === 0 ? (
              <p className="text-sm text-[var(--docs-muted)]">
                No personas yet. Create your first persona to customize chat behavior.
              </p>
            ) : (
              <div className="space-y-3">
                {personas.map((persona) => {
                  const preview =
                    persona.systemPrompt.length > 160
                      ? `${persona.systemPrompt.slice(0, 160)}...`
                      : persona.systemPrompt
                  const toolsLabel = [
                    persona.webSearchEnabled && 'Web search',
                    persona.yahooFinanceEnabled && 'Yahoo Finance',
                  ]
                    .filter(Boolean)
                    .join(', ') || 'None'

                  return (
                    <article
                      key={persona.id}
                      className="rounded-lg border border-[var(--docs-border)] bg-[var(--docs-bg)] p-4"
                    >
                      <h3 className="text-base font-semibold text-[var(--docs-text)]">
                        {persona.name}
                      </h3>
                      <p className="mt-1 text-sm text-[var(--docs-muted)]">
                        {persona.description}
                      </p>
                      <p className="mt-1 text-xs text-[var(--docs-muted)]">
                        Tools: {toolsLabel}
                      </p>
                      <p className="mt-2 rounded border border-[var(--docs-border)] bg-white px-2 py-1 font-mono text-xs text-[var(--docs-muted)]">
                        {preview || 'No prompt preview available.'}
                      </p>
                      <div className="mt-3 flex gap-2">
                        <button
                          type="button"
                          onClick={() => editPersona(persona)}
                          className="rounded bg-[var(--docs-accent)] px-3 py-1.5 text-xs font-medium text-white hover:opacity-90"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          disabled={deletingId === persona.id}
                          onClick={() => void deletePersona(persona.id, persona.name)}
                          className="rounded border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {deletingId === persona.id ? 'Deleting...' : 'Delete'}
                        </button>
                      </div>
                    </article>
                  )
                })}
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  )
}
