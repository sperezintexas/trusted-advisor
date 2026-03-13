'use client'

import { useState, useEffect, useCallback } from 'react'
import {
  fetchPersonaFiles,
  addPersonaFile,
  removePersonaFile,
  indexPersonaFile,
  formatFileSize,
  getStatusColor,
  type PersonaFileView,
  type AddFileRequest,
} from '@/lib/persona-files'

type PersonaFilesProps = {
  personaId: string
  personaName: string
}

export default function PersonaFiles({ personaId, personaName }: PersonaFilesProps) {
  const [files, setFiles] = useState<PersonaFileView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showAddForm, setShowAddForm] = useState(false)
  const [addingFile, setAddingFile] = useState(false)
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [indexingId, setIndexingId] = useState<string | null>(null)

  const [newFile, setNewFile] = useState<AddFileRequest>({
    sourceFileId: '',
    name: '',
    sourceUrl: '',
    sourceType: 'GOOGLE_DRIVE',
  })
  const [fileContent, setFileContent] = useState('')

  const loadFiles = useCallback(async () => {
    setLoading(true)
    setError(null)
    const result = await fetchPersonaFiles(personaId)
    if (result) {
      setFiles(result.files)
    } else {
      setError('Failed to load files')
    }
    setLoading(false)
  }, [personaId])

  useEffect(() => {
    loadFiles()
  }, [loadFiles])

  const handleAddFile = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newFile.name || !newFile.sourceFileId) return

    setAddingFile(true)
    setError(null)

    const result = await addPersonaFile(personaId, newFile)
    if (result?.success) {
      setShowAddForm(false)
      setNewFile({ sourceFileId: '', name: '', sourceUrl: '', sourceType: 'GOOGLE_DRIVE' })
      await loadFiles()
    } else {
      setError(result?.message || 'Failed to add file')
    }
    setAddingFile(false)
  }

  const handleRemoveFile = async (fileId: string) => {
    if (!confirm('Remove this file from the persona?')) return

    setRemovingId(fileId)
    const result = await removePersonaFile(personaId, fileId)
    if (result?.success) {
      await loadFiles()
    } else {
      setError(result?.message || 'Failed to remove file')
    }
    setRemovingId(null)
  }

  const handleIndexFile = async (fileId: string) => {
    if (!fileContent.trim()) {
      setError('Please paste the file content to index')
      return
    }

    setIndexingId(fileId)
    const result = await indexPersonaFile(personaId, fileId, fileContent)
    if (result?.success) {
      setFileContent('')
      await loadFiles()
    } else {
      setError(result?.message || 'Failed to index file')
    }
    setIndexingId(null)
  }

  return (
    <div className="rounded-lg border border-[var(--docs-border)] bg-white p-4">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-[var(--docs-text)]">
          Attached Files
        </h3>
        <button
          type="button"
          onClick={() => setShowAddForm(!showAddForm)}
          className="text-sm text-[var(--docs-accent)] hover:underline"
        >
          {showAddForm ? 'Cancel' : '+ Add File'}
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {showAddForm && (
        <form onSubmit={handleAddFile} className="mb-4 space-y-3 rounded-lg border border-[var(--docs-border)] bg-[var(--docs-code-bg)] p-4">
          <div>
            <label className="block text-xs font-medium text-[var(--docs-muted)] mb-1">
              File Name
            </label>
            <input
              type="text"
              value={newFile.name}
              onChange={(e) => setNewFile({ ...newFile, name: e.target.value })}
              placeholder="e.g., SIE Study Guide.pdf"
              className="w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--docs-muted)] mb-1">
              Source Type
            </label>
            <select
              value={newFile.sourceType}
              onChange={(e) => setNewFile({ ...newFile, sourceType: e.target.value })}
              className="w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
            >
              <option value="GOOGLE_DRIVE">Google Drive</option>
              <option value="URL">URL</option>
              <option value="UPLOAD">Manual Upload</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--docs-muted)] mb-1">
              Source ID / URL
            </label>
            <input
              type="text"
              value={newFile.sourceFileId}
              onChange={(e) => setNewFile({ ...newFile, sourceFileId: e.target.value })}
              placeholder="Google Drive file ID or URL"
              className="w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--docs-muted)] mb-1">
              Source URL (optional)
            </label>
            <input
              type="text"
              value={newFile.sourceUrl || ''}
              onChange={(e) => setNewFile({ ...newFile, sourceUrl: e.target.value })}
              placeholder="https://drive.google.com/..."
              className="w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={addingFile}
            className="w-full rounded-lg bg-black px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {addingFile ? 'Adding...' : 'Add File'}
          </button>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-[var(--docs-muted)]">Loading files...</p>
      ) : files.length === 0 ? (
        <p className="text-sm text-[var(--docs-muted)]">
          No files attached. Add files to give this persona reference context.
        </p>
      ) : (
        <div className="space-y-3">
          {files.map((file) => (
            <div
              key={file.id}
              className="rounded-lg border border-[var(--docs-border)] p-3"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-[var(--docs-text)] truncate">
                      {file.name}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${getStatusColor(file.status)}`}
                    >
                      {file.status}
                    </span>
                  </div>
                  <div className="mt-1 text-xs text-[var(--docs-muted)]">
                    {file.sourceType} • {formatFileSize(file.sizeBytes)}
                    {file.chunkCount > 0 && ` • ${file.chunkCount} chunks`}
                  </div>
                  {file.lastError && (
                    <div className="mt-1 text-xs text-red-600">{file.lastError}</div>
                  )}
                </div>
                <div className="flex items-center gap-2 ml-2">
                  {file.status === 'PENDING' && (
                    <button
                      type="button"
                      onClick={() => setIndexingId(indexingId === file.id ? null : file.id)}
                      className="text-xs text-[var(--docs-accent)] hover:underline"
                    >
                      Index
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => handleRemoveFile(file.id)}
                    disabled={removingId === file.id}
                    className="text-xs text-red-600 hover:underline disabled:opacity-50"
                  >
                    {removingId === file.id ? '...' : 'Remove'}
                  </button>
                </div>
              </div>

              {indexingId === file.id && (
                <div className="mt-3 space-y-2">
                  <label className="block text-xs font-medium text-[var(--docs-muted)]">
                    Paste file content to index:
                  </label>
                  <textarea
                    value={fileContent}
                    onChange={(e) => setFileContent(e.target.value)}
                    rows={6}
                    className="w-full rounded-lg border border-[var(--docs-border)] bg-white px-3 py-2 text-sm font-mono"
                    placeholder="Paste the text content of the file here..."
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => handleIndexFile(file.id)}
                      disabled={!fileContent.trim()}
                      className="rounded-lg bg-[var(--docs-accent)] px-3 py-1.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
                    >
                      Index Content
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setIndexingId(null)
                        setFileContent('')
                      }}
                      className="rounded-lg border border-[var(--docs-border)] px-3 py-1.5 text-sm text-[var(--docs-muted)] hover:bg-[var(--docs-code-bg)]"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
