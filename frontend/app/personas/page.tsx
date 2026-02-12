'use client'

import { useState, useEffect } from 'react'
import AppHeader from '../components/AppHeader'

interface Persona {
  id: string
  name: string
  description: string
  systemPrompt: string
}

export default function PersonasPage() {
  const [personas, setPersonas] = useState<Persona[]>([])
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', description: '', systemPrompt: '' })
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadPersonas()
  }, [])

  const loadPersonas = async () => {
    const res = await fetch('/api/personas')
    const data = await res.json()
    setPersonas(data)
  }

  const savePersona = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    const method = editingId ? 'PUT' : 'POST'
    const url = editingId ? `/api/personas/${editingId}` : '/api/personas'
    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form),
    })
    setEditingId(null)
    setForm({ name: '', description: '', systemPrompt: '' })
    loadPersonas()
    setLoading(false)
  }

  const editPersona = (persona: Persona) => {
    setEditingId(persona.id)
    setForm({
      name: persona.name,
      description: persona.description,
      systemPrompt: persona.systemPrompt,
    })
  }

  const deletePersona = async (id: string) => {
    if (!confirm('Delete?')) return
    await fetch(`/api/personas/${id}`, { method: 'DELETE' })
    loadPersonas()
  }

  return (
    <div className="min-h-screen flex flex-col bg-[var(--docs-bg)]">
      <AppHeader />
      <main className="p-8 max-w-6xl mx-auto flex-1">
      <h1 className="text-2xl font-semibold mb-2 text-[var(--docs-text)]">Manage Personas</h1>
      <p className="mb-8 text-sm text-[var(--docs-muted)]">Create and edit expert personas used in chat.</p>
      <form onSubmit={savePersona} className="bg-white p-6 rounded-lg shadow mb-8">
        <input
          placeholder="Name"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          className="w-full p-2 border rounded mb-4"
          required
        />
        <textarea
          placeholder="Description"
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
          className="w-full p-2 border rounded mb-4 h-24"
          required
        />
        <textarea
          placeholder="System Prompt (full persona description)"
          value={form.systemPrompt}
          onChange={(e) => setForm({ ...form, systemPrompt: e.target.value })}
          className="w-full p-2 border rounded mb-4 h-48"
          required
        />
        <button
          type="submit"
          disabled={loading}
          className="px-6 py-2 bg-green-500 text-white rounded hover:bg-green-600"
        >
          {editingId ? 'Update' : 'Create'} Persona
        </button>
        {editingId && (
          <button
            type="button"
            onClick={() => {
              setEditingId(null)
              setForm({ name: '', description: '', systemPrompt: '' })
            }}
            className="ml-4 px-6 py-2 bg-gray-500 text-white rounded"
          >
            Cancel
          </button>
        )}
      </form>
      <div className="grid gap-4">
        {personas.map((p) => (
          <div key={p.id} className="bg-white p-6 rounded-lg shadow flex justify-between items-start">
            <div>
              <h3 className="font-bold text-xl">{p.name}</h3>
              <p className="text-gray-600">{p.description}</p>
              <p className="mt-2 text-sm bg-yellow-100 p-2 rounded">
                Prompt preview: {p.systemPrompt.substring(0, 100)}...
              </p>
            </div>
            <div className="flex gap-2">
              <button onClick={() => editPersona(p)} className="px-4 py-1 bg-blue-500 text-white rounded">
                Edit
              </button>
              <button
                onClick={() => deletePersona(p.id)}
                className="px-4 py-1 bg-red-500 text-white rounded"
              >
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
      </main>
    </div>
  )
}
