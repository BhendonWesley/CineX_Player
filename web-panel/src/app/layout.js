'use client'

import './globals.css'
import Sidebar from '@/components/Sidebar'
import MobileHeader from '@/components/MobileHeader'
import { usePathname } from 'next/navigation'
import { useState } from 'react'

export default function RootLayout({ children }) {
  const pathname = usePathname()
  const isLoginPage = pathname === '/login'
  const [isSidebarOpen, setSidebarOpen] = useState(false)

  return (
    <html lang="pt-BR">
      <body>
        {!isLoginPage && (
          <>
            <MobileHeader onMenuClick={() => setSidebarOpen(true)} />
            <Sidebar isOpen={isSidebarOpen} onClose={() => setSidebarOpen(false)} />
          </>
        )}
        <main 
          className={!isLoginPage ? "main-content" : ""}
        >
          {children}
        </main>
      </body>
    </html>
  )
}
