'use client'

// CineX Web Panel - v1.0.2 - Identity Verified
import './globals.css'
import Sidebar from '@/components/Sidebar'
import { usePathname } from 'next/navigation'

export default function RootLayout({ children }) {
  const pathname = usePathname()
  const isLoginPage = pathname === '/login'

  return (
    <html lang="pt-BR">
      <body>
        {!isLoginPage && <Sidebar />}
        <main style={{ marginLeft: !isLoginPage ? '280px' : '0', transition: 'margin 0.3s ease' }}>
          {children}
        </main>
      </body>
    </html>
  )
}
