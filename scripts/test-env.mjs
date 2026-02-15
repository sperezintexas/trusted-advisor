#!/usr/bin/env node
/**
 * CLI test using .env: checks xAI connectivity (XAI_API_KEY). Optional: AUTH_SECRET is used by the app for API key auth.
 * Run from repo root: node scripts/test-env.mjs
 * Loads .env from repo root.
 */

import { readFileSync, existsSync } from 'fs'
import { fileURLToPath } from 'url'
import path from 'path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function loadEnv() {
  const envPath = path.resolve(__dirname, '..', '.env')
  if (!existsSync(envPath)) {
    console.error('No .env at repo root. Expected:', envPath)
    process.exit(1)
  }
  console.log('Loading .env from repo root\n')
  const content = readFileSync(envPath, 'utf-8')
  for (const line of content.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const eq = trimmed.indexOf('=')
    if (eq <= 0) continue
    const key = trimmed.slice(0, eq).trim()
    let value = trimmed.slice(eq + 1).trim()
    if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1).replace(/\\"/g, '"')
    if (key && process.env[key] === undefined) process.env[key] = value
  }
}

loadEnv()

const authSecret = process.env.AUTH_SECRET?.trim()
const xaiKey = process.env.XAI_API_KEY?.trim()

if (authSecret) {
  console.log('AUTH_SECRET: set (API key auth enabled)')
} else {
  console.log('AUTH_SECRET: not set — set it in .env to use API key login')
}
console.log('')

if (!xaiKey) {
  console.log('xAI: XAI_API_KEY not set — skip')
} else {
  try {
    const res = await fetch('https://api.x.ai/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${xaiKey}`,
      },
      body: JSON.stringify({
        model: 'grok-4',
        messages: [{ role: 'user', content: 'Reply with exactly: OK' }],
        max_tokens: 10,
      }),
    })
    const data = await res.json().catch(() => ({}))
    if (res.ok && data.choices?.[0]?.message?.content) {
      console.log('xAI: OK —', data.choices[0].message.content.trim())
    } else {
      console.log('xAI: request failed', res.status, data?.error?.message || JSON.stringify(data).slice(0, 120))
    }
  } catch (e) {
    console.log('xAI: error', e.message)
  }
}
