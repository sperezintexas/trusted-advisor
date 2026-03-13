#!/usr/bin/env node
/**
 * One-off admin script to remove a user from MongoDB.
 *
 * Usage:
 *   node scripts/remove-user.mjs --email user@example.com
 *   node scripts/remove-user.mjs --email user@example.com --confirm
 *   node scripts/remove-user.mjs --email user@example.com --confirm --purge
 *
 * Behavior:
 *   - Always shows a dry-run preview first.
 *   - Without --confirm, no data is deleted.
 *   - Default delete scope: users + accessRequests for the email.
 *   - With --purge: also deletes chatHistory, coachUserProgress,
 *     coachExamAttempts, and coachSessions for that userId/email.
 */

import { MongoClient } from 'mongodb'
import { config } from 'dotenv'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
config({ path: resolve(__dirname, '../.env') })

function getMongoUri() {
  if (process.env.MONGODB_URI_B64) {
    return Buffer.from(process.env.MONGODB_URI_B64, 'base64').toString('utf-8')
  }
  return process.env.MONGODB_URI || 'mongodb://localhost:27017/trusted-advisor'
}

function parseArgs(argv) {
  const args = {
    email: '',
    confirm: false,
    purge: false,
    help: false,
  }

  for (let i = 0; i < argv.length; i++) {
    const token = argv[i]
    if (token === '--help' || token === '-h') {
      args.help = true
    } else if (token === '--confirm' || token === '--yes') {
      args.confirm = true
    } else if (token === '--purge') {
      args.purge = true
    } else if (token === '--email' || token === '-e') {
      args.email = (argv[i + 1] || '').trim().toLowerCase()
      i += 1
    }
  }

  return args
}

function printHelp() {
  console.log(`
Remove a user (one-off admin script)

Required:
  --email, -e <email>   User email to remove

Options:
  --confirm, --yes      Actually perform deletion (otherwise dry run)
  --purge               Also delete related user data
  --help, -h            Show this help

Examples:
  node scripts/remove-user.mjs --email user@example.com
  node scripts/remove-user.mjs --email user@example.com --confirm
  node scripts/remove-user.mjs --email user@example.com --confirm --purge
`)
}

async function countDocuments(db, collection, filter) {
  return db.collection(collection).countDocuments(filter)
}

async function main() {
  const { email, confirm, purge, help } = parseArgs(process.argv.slice(2))
  if (help) {
    printHelp()
    return
  }

  if (!email || !email.includes('@')) {
    console.error('Error: valid --email is required')
    printHelp()
    process.exit(1)
  }

  const uri = getMongoUri()
  const dbName = process.env.MONGODB_DATABASE || 'trusted-advisor'
  const client = new MongoClient(uri)

  try {
    await client.connect()
    const db = client.db(dbName)

    const users = db.collection('users')
    const accessRequests = db.collection('accessRequests')

    const userDoc = await users.findOne({ email })
    const userCount = userDoc ? 1 : 0
    const accessRequestCount = await countDocuments(db, 'accessRequests', { email })

    let chatHistoryCount = 0
    let progressCount = 0
    let attemptsCount = 0
    let sessionsCount = 0

    if (purge) {
      chatHistoryCount = await countDocuments(db, 'chatHistory', { userId: email })
      progressCount = await countDocuments(db, 'coachUserProgress', { userId: email })
      attemptsCount = await countDocuments(db, 'coachExamAttempts', { userId: email })
      sessionsCount = await countDocuments(db, 'coachSessions', { userId: email })
    }

    console.log(`\nUser removal preview for: ${email}`)
    console.log(`Database: ${dbName}`)
    console.log(`\nWould delete:`)
    console.log(`- users: ${userCount}`)
    console.log(`- accessRequests: ${accessRequestCount}`)
    if (purge) {
      console.log(`- chatHistory: ${chatHistoryCount}`)
      console.log(`- coachUserProgress: ${progressCount}`)
      console.log(`- coachExamAttempts: ${attemptsCount}`)
      console.log(`- coachSessions: ${sessionsCount}`)
    }

    if (!confirm) {
      console.log('\nDry run only. Re-run with --confirm to execute.')
      return
    }

    const userDeleteResult = await users.deleteOne({ email })
    const accessDeleteResult = await accessRequests.deleteMany({ email })

    let chatDeleteResult = { deletedCount: 0 }
    let progressDeleteResult = { deletedCount: 0 }
    let attemptsDeleteResult = { deletedCount: 0 }
    let sessionsDeleteResult = { deletedCount: 0 }

    if (purge) {
      chatDeleteResult = await db.collection('chatHistory').deleteMany({ userId: email })
      progressDeleteResult = await db.collection('coachUserProgress').deleteMany({ userId: email })
      attemptsDeleteResult = await db.collection('coachExamAttempts').deleteMany({ userId: email })
      sessionsDeleteResult = await db.collection('coachSessions').deleteMany({ userId: email })
    }

    console.log('\nDeleted:')
    console.log(`- users: ${userDeleteResult.deletedCount}`)
    console.log(`- accessRequests: ${accessDeleteResult.deletedCount}`)
    if (purge) {
      console.log(`- chatHistory: ${chatDeleteResult.deletedCount}`)
      console.log(`- coachUserProgress: ${progressDeleteResult.deletedCount}`)
      console.log(`- coachExamAttempts: ${attemptsDeleteResult.deletedCount}`)
      console.log(`- coachSessions: ${sessionsDeleteResult.deletedCount}`)
    }
    console.log('\nDone.')
  } catch (err) {
    console.error('Error:', err instanceof Error ? err.message : String(err))
    process.exit(1)
  } finally {
    await client.close()
  }
}

main()
