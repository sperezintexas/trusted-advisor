#!/usr/bin/env node
/**
 * Admin utility for access requests.
 *
 * Commands:
 *   node scripts/access-request-admin.mjs check --email user@example.com
 *   node scripts/access-request-admin.mjs approve --email user@example.com --note "Approved"
 *   node scripts/access-request-admin.mjs approve --id <requestId> --note "Approved"
 *
 * Optional flags:
 *   --admin-email admin@example.com
 *   --api http://localhost:8080/api
 */

import { config } from 'dotenv'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
config({ path: resolve(__dirname, '../.env') })

function parseArgs(argv) {
  const [command = '', ...rest] = argv
  const out = {
    command,
    email: '',
    id: '',
    note: 'Approved by admin task',
    adminEmail: '',
    api: '',
    help: false,
  }

  for (let i = 0; i < rest.length; i++) {
    const token = rest[i]
    if (token === '--help' || token === '-h') out.help = true
    else if (token === '--email' || token === '-e') {
      out.email = (rest[i + 1] || '').trim().toLowerCase()
      i += 1
    } else if (token === '--id') {
      out.id = (rest[i + 1] || '').trim()
      i += 1
    } else if (token === '--note') {
      out.note = (rest[i + 1] || '').trim() || out.note
      i += 1
    } else if (token === '--admin-email') {
      out.adminEmail = (rest[i + 1] || '').trim().toLowerCase()
      i += 1
    } else if (token === '--api') {
      out.api = (rest[i + 1] || '').trim()
      i += 1
    }
  }
  return out
}

function printHelp() {
  console.log(`
Access Request Admin Tasks

Commands:
  check    Check pending request for a user email
  approve  Approve request by id or by user email

Examples:
  node scripts/access-request-admin.mjs check --email user@example.com
  node scripts/access-request-admin.mjs approve --email user@example.com --note "Approved"
  node scripts/access-request-admin.mjs approve --id 69b377... --note "Approved"

Options:
  --admin-email <email>  Override admin identity (defaults to first ADMIN_EMAILS)
  --api <url>            Override API base (default from env, then localhost)
`)
}

function getApiBase(apiArg) {
  const fromEnv = process.env.BACKEND_URL || process.env.NEXT_PUBLIC_BACKEND_URL
  const raw = apiArg || fromEnv || 'http://localhost:8080'
  return raw.endsWith('/api') ? raw : `${raw}/api`
}

function getAdminEmail(adminArg) {
  if (adminArg) return adminArg
  const raw = process.env.ADMIN_EMAILS || ''
  const first = raw.split(',').map((s) => s.trim().toLowerCase()).filter(Boolean)[0]
  return first || ''
}

function headers(authSecret, adminEmail) {
  return {
    'Content-Type': 'application/json',
    'X-API-Key': authSecret,
    'X-User-Id': adminEmail,
  }
}

async function fetchPending(apiBase, authSecret, adminEmail) {
  const res = await fetch(`${apiBase}/admin/access-requests?status=PENDING`, {
    headers: headers(authSecret, adminEmail),
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`list failed: ${res.status} ${res.statusText} ${body}`)
  }
  const data = await res.json()
  const requests = Array.isArray(data?.requests) ? data.requests : []
  return requests
}

async function runCheck({ apiBase, authSecret, adminEmail, email }) {
  if (!email) throw new Error('--email is required for check')
  const requests = await fetchPending(apiBase, authSecret, adminEmail)
  const match = requests.filter((r) => (r?.email || '').toLowerCase() === email)

  console.log(`Admin: ${adminEmail}`)
  console.log(`Pending total: ${requests.length}`)
  if (match.length === 0) {
    console.log(`No pending access request found for ${email}`)
    return
  }

  for (const req of match) {
    console.log('\nFound pending request:')
    console.log(`- id: ${req.id}`)
    console.log(`- email: ${req.email}`)
    console.log(`- displayName: ${req.displayName || '-'}`)
    console.log(`- createdAt: ${req.createdAt || '-'}`)
  }
}

async function runApprove({ apiBase, authSecret, adminEmail, email, id, note }) {
  let requestId = id
  if (!requestId && !email) {
    throw new Error('approve requires --id or --email')
  }

  if (!requestId && email) {
    const requests = await fetchPending(apiBase, authSecret, adminEmail)
    const match = requests.filter((r) => (r?.email || '').toLowerCase() === email)
    if (match.length === 0) throw new Error(`No pending request found for ${email}`)
    if (match.length > 1) throw new Error(`Multiple pending requests found for ${email}; use --id`)
    requestId = match[0].id
  }

  const res = await fetch(`${apiBase}/admin/access-requests/${requestId}/approve`, {
    method: 'POST',
    headers: headers(authSecret, adminEmail),
    body: JSON.stringify({ note }),
  })
  const body = await res.text()
  if (!res.ok) {
    throw new Error(`approve failed: ${res.status} ${res.statusText} ${body}`)
  }

  console.log(`Approved request ${requestId}`)
  if (body) {
    try {
      const data = JSON.parse(body)
      if (data?.request?.email) console.log(`User: ${data.request.email}`)
      if (data?.message) console.log(`Message: ${data.message}`)
    } catch {
      console.log(body)
    }
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.help || !args.command || args.command === '--help' || args.command === '-h') {
    printHelp()
    return
  }

  const authSecret = (process.env.AUTH_SECRET || '').trim()
  const adminEmail = getAdminEmail(args.adminEmail)
  const apiBase = getApiBase(args.api)

  if (!authSecret) throw new Error('AUTH_SECRET is not set')
  if (!adminEmail) throw new Error('Admin email not found; set ADMIN_EMAILS or pass --admin-email')

  if (args.command === 'check') {
    await runCheck({
      apiBase,
      authSecret,
      adminEmail,
      email: args.email,
    })
    return
  }

  if (args.command === 'approve') {
    await runApprove({
      apiBase,
      authSecret,
      adminEmail,
      email: args.email,
      id: args.id,
      note: args.note,
    })
    return
  }

  throw new Error(`Unknown command: ${args.command}`)
}

main().catch((err) => {
  console.error('Error:', err instanceof Error ? err.message : String(err))
  process.exit(1)
})
