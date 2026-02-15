#!/usr/bin/env node
/**
 * X (Twitter) test: verify credentials (login) and post "Hello world".
 * Uses OAuth 1.0a: X_CONSUMER_KEY, X_CONSUMER_SECRET (or X_CONSUMER_SECRET_KEY), X_ACCESS_TOKEN, X_ACCESS_TOKEN_SECRET.
 *
 * From repo root:
 *   cd frontend && npm run test:x-post
 * Or:
 *   cd frontend && node scripts/test-x-post.mjs
 *
 * Loads .env from repo root (../.env).
 */

import { readFileSync, existsSync } from 'fs'
import { fileURLToPath } from 'url'
import path from 'path'
import { createRequire } from 'module'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function loadEnv() {
  const envPath = path.resolve(__dirname, '..', '..', '.env')
  if (!existsSync(envPath)) {
    console.error('No .env at repo root. Expected:', envPath)
    process.exit(1)
  }
  console.log('Loading .env from repo root')
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

const consumerKey = process.env.X_CONSUMER_KEY?.trim()
const consumerSecret =
  process.env.X_CONSUMER_SECRET?.trim() || process.env.X_CONSUMER_SECRET_KEY?.trim()
const accessToken = process.env.X_ACCESS_TOKEN?.trim()
const accessTokenSecret = process.env.X_ACCESS_TOKEN_SECRET?.trim()

if (!consumerKey || !consumerSecret || !accessToken || !accessTokenSecret) {
  console.error(
    'Missing X posting credentials in .env. Set: X_CONSUMER_KEY, X_CONSUMER_SECRET (or X_CONSUMER_SECRET_KEY), X_ACCESS_TOKEN, X_ACCESS_TOKEN_SECRET'
  )
  console.error('Get them from developer.x.com → your app → Keys and tokens (Consumer Keys + Access Token and Secret)')
  process.exit(1)
}

const require = createRequire(import.meta.url)
const { TwitterApi } = require('twitter-api-v2')

const client = new TwitterApi({
  appKey: consumerKey,
  appSecret: consumerSecret,
  accessToken,
  accessSecret: accessTokenSecret,
})

async function main() {
  try {
    console.log('Verifying X credentials (v2.me)...')
    const me = await client.v2.me()
    const username = me.data?.username ?? '?'
    console.log('Login OK. Logged in as: @' + username)

    console.log('Posting tweet: "Hello world"...')
    const res = await client.v2.tweet('Hello world')
    console.log('Posted:', 'https://x.com/i/status/' + res.data.id)
  } catch (e) {
    console.error('X API error:', e.message || e)
    if (e.code) console.error('  code:', e.code)
    if (e.data) console.error('  data:', JSON.stringify(e.data))
    process.exit(1)
  }
}

main()
