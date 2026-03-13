'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { LayoutDashboard, Smartphone, LogOut } from 'lucide-react'
import Image from 'next/image'

export default function Sidebar({ isOpen, onClose }) {
    const pathname = usePathname()
    const router = useRouter()

    const menuItems = [
        { name: 'Dashboard', icon: <LayoutDashboard size={20} />, path: '/' },
        { name: 'Dispositivos', icon: <Smartphone size={20} />, path: '/devices' },
    ]

    const handleLogout = async () => {
        await fetch('/api/logout', { method: 'POST' })
        window.location.href = '/login'
    }

    return (
        <>
            {/* Overlay para fechar sidebar no mobile ao clicar fora */}
            {isOpen && (
                <div 
                    onClick={onClose}
                    style={{
                        position: 'fixed',
                        inset: 0,
                        background: 'rgba(0,0,0,0.5)',
                        backdropFilter: 'blur(4px)',
                        zIndex: 1000,
                    }}
                    className="desktop-hide"
                />
            )}

            <aside 
                className={`sidebar ${isOpen ? 'sidebar-open' : ''}`}
                style={{
                    width: '280px',
                    height: '100vh',
                    background: 'var(--bg-dark)',
                    borderRight: '1px solid var(--border-color)',
                    padding: '32px 20px',
                    display: 'flex',
                    flexDirection: 'column',
                    position: 'fixed',
                    left: 0,
                    top: 0,
                    zIndex: 1001,
                    transition: 'transform 0.3s ease'
                }}
            >
                <style jsx>{`
                    @media screen and (max-width: 1024px) {
                        .sidebar {
                            transform: translateX(-100%);
                        }
                        .sidebar-open {
                            transform: translateX(0);
                        }
                        .desktop-hide {
                            display: block !important;
                        }
                    }
                    @media screen and (min-width: 1025px) {
                        .desktop-hide {
                            display: none !important;
                        }
                        .sidebar {
                            transform: translateX(0) !important;
                        }
                    }
                `}</style>

                <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '48px', padding: '0 12px', position: 'relative' }}>
                    <Image src="/cinex-logo-final.png" alt="CineX" width={80} height={80} style={{ objectFit: 'contain' }} />
                </div>

                <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {menuItems.map((item) => {
                        const isActive = pathname === item.path
                        return (
                            <Link 
                                key={item.path} 
                                href={item.path} 
                                onClick={() => {
                                    if (onClose) onClose()
                                }}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px',
                                    padding: '12px 16px',
                                    borderRadius: '12px',
                                    color: isActive ? 'var(--light-gold)' : 'var(--text-secondary)',
                                    background: isActive ? 'rgba(216, 166, 58, 0.1)' : 'transparent',
                                    transition: 'all 0.3s ease',
                                    border: isActive ? '1px solid var(--border-color)' : '1px solid transparent'
                                }}
                            >
                                {item.icon}
                                <span style={{ fontWeight: isActive ? '600' : '400' }}>{item.name}</span>
                            </Link>
                        )
                    })}
                </nav>

                <button onClick={handleLogout} style={{
                    display: 'flex', alignItems: 'center', gap: '12px', padding: '12px 16px',
                    color: 'var(--highlight-red)', background: 'transparent', border: 'none', cursor: 'pointer',
                    marginTop: 'auto', borderRadius: '12px'
                }}>
                    <LogOut size={20} />
                    <span>Sair do Painel</span>
                </button>
            </aside>
        </>
    )
}
