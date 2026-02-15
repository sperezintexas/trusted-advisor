import path from 'path'
import { fileURLToPath } from 'url'
import dotenv from 'dotenv'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
// Load repo-root .env so frontend and backend share the same env file
dotenv.config({ path: path.resolve(__dirname, '..', '.env') })

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    const backend =
      process.env.NEXT_PUBLIC_BACKEND_URL ||
      process.env.BACKEND_URL ||
      'http://localhost:8080'
    return [
      { source: '/api/:path*', destination: `${backend}/api/:path*` },
    ]
  },
}

export default nextConfig