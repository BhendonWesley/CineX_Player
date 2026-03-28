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
      <head>
        <link rel="manifest" href="/manifest.json" />
        <meta name="theme-color" content="#B21E2B" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
        <meta name="apple-mobile-web-app-title" content="CineX Painel" />
        <link rel="apple-touch-icon" href="/icons/icon-192.png" />
        <script
          dangerouslySetInnerHTML={{
            __html: `
              if ('serviceWorker' in navigator) {
                window.addEventListener('load', () => {
                  navigator.serviceWorker.register('/sw.js')
                })
              }
            `
          }}
        />
      </head>
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
