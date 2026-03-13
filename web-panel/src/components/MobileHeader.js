'use client'

import { Menu } from 'lucide-react'
import Image from 'next/image'

export default function MobileHeader({ onMenuClick }) {
    return (
        <header style={{
            display: 'none',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '16px 20px',
            background: 'var(--bg-dark)',
            borderBottom: '1px solid var(--border-color)',
            position: 'sticky',
            top: 0,
            zIndex: 100,
        }} className="mobile-flex">
            <style jsx>{`
                @media screen and (max-width: 1024px) {
                    .mobile-flex {
                        display: flex !important;
                    }
                }
            `}</style>
            
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Image src="/cinex-logo-final.png" alt="CineX" width={32} height={32} style={{ objectFit: 'contain' }} />
                <span style={{ fontWeight: '700', fontSize: '13px', color: 'var(--premium-gold)', letterSpacing: '0.5px' }}>PAINEL DE CONTROLE</span>
            </div>

            <button 
                onClick={onMenuClick}
                style={{
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-primary)',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '8px'
                }}
            >
                <Menu size={24} />
            </button>
        </header>
    )
}
