'use client'

import { useEffect, useState } from 'react'

export default function Template({ children }) {
  const [isMounted, setIsMounted] = useState(false)

  useEffect(() => {
    setIsMounted(true)
  }, [])

  return (
    <div className={`page-transition ${isMounted ? 'page-transition-enter' : ''}`}>
      {children}
    </div>
  )
}
