#!/usr/bin/env node
/**
 * Seed admin users from ADMIN_EMAILS in .env
 * Usage: node scripts/seed-admin-users.mjs
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

async function main() {
  const adminEmails = process.env.ADMIN_EMAILS
  if (!adminEmails) {
    console.error('Error: ADMIN_EMAILS not set in .env')
    process.exit(1)
  }

  const emails = adminEmails.split(',').map(e => e.trim().toLowerCase()).filter(Boolean)
  if (emails.length === 0) {
    console.error('Error: No valid emails found in ADMIN_EMAILS')
    process.exit(1)
  }

  console.log(`Found ${emails.length} admin email(s): ${emails.join(', ')}`)

  const uri = getMongoUri()
  const dbName = process.env.MONGODB_DATABASE || 'trusted-advisor'
  
  const client = new MongoClient(uri)
  
  try {
    await client.connect()
    console.log(`Connected to MongoDB, database: ${dbName}`)
    
    const db = client.db(dbName)
    const usersCollection = db.collection('users')
    
    for (const email of emails) {
      const existing = await usersCollection.findOne({ email })
      if (existing) {
        console.log(`✓ User already exists: ${email}`)
        continue
      }
      
      const username = email.split('@')[0]
      const now = new Date()
      
      await usersCollection.insertOne({
        email,
        username,
        displayName: null,
        profileImageUrl: null,
        registered: false,
        createdAt: now,
        updatedAt: now,
        firstLoginAt: null,
        lastLoginAt: null
      })
      
      console.log(`✓ Created user: ${email}`)
    }
    
    console.log('\nDone! Admin users are now in the database.')
    
  } catch (err) {
    console.error('Error:', err.message)
    process.exit(1)
  } finally {
    await client.close()
  }
}

main()
