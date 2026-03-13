import path from 'path'
import { fileURLToPath } from 'url'
import dotenv from 'dotenv'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
// Load repo-root .env so frontend and backend share the same env file
dotenv.config({ path: path.resolve(__dirname, '..', '.env') })

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  env: {
    NEXT_PUBLIC_APP_VERSION:
      process.env.npm_package_version || process.env.APP_VERSION || '0.0.0',
  },
  async rewrites() {
    const backend =
      process.env.NEXT_PUBLIC_BACKEND_URL ||
      process.env.BACKEND_URL ||
      'http://localhost:8080'
    return [
      { source: '/api/:path*', destination: `${backend}/api/:path*` },
      { source: '/oauth2/:path*', destination: `${backend}/oauth2/:path*` },
      { source: '/login/oauth2/:path*', destination: `${backend}/login/oauth2/:path*` },
      { source: '/logout', destination: `${backend}/logout` },
    ]
  },
}

export default nextConfig