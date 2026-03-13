'use client'

import { useEffect, useState } from 'react'

export default function Template({ children }) {
  const [isMounted, setIsMounted] = useState(false)

  useEffect(() => {
    const frame = requestAnimationFrame(() => setIsMounted(true))
    return () => cancelAnimationFrame(frame)
  }, [])

  return (
    <div className={`page-transition ${isMounted ? 'page-transition-enter' : ''}`}>
      {children}
    </div>
  )
}
