'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { LayoutDashboard, Smartphone, LogOut } from 'lucide-react'

export default function Sidebar() {
    const pathname = usePathname()
    const router = useRouter()

    const menuItems = [
        { name: 'Dashboard', icon: <LayoutDashboard size={20} />, path: '/' },
        { name: 'Dispositivos', icon: <Smartphone size={20} />, path: '/devices' },
    ]

    const handleLogout = async () => {
        await fetch('/api/logout', { method: 'POST' })
        router.push('/login')
    }

    return (
        <aside style={{
            width: '280px',
            height: '100vh',
            background: 'var(--bg-dark)',
            borderRight: '1px solid var(--border-color)',
            padding: '32px 20px',
            display: 'flex',
            flexDirection: 'column',
            position: 'fixed',
            left: 0,
            top: 0
        }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '48px', padding: '0 12px' }}>
                <div style={{
                    width: '32px', height: '32px', background: 'var(--primary-red)',
                    borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                    <span style={{ fontWeight: '900', fontSize: '18px' }}>X</span>
                </div>
                <h2 className="glow-text" style={{ fontSize: '24px' }}>CineX</h2>
            </div>

            <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {menuItems.map((item) => {
                    const isActive = pathname === item.path
                    return (
                        <Link key={item.path} href={item.path} style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '12px',
                            padding: '12px 16px',
                            borderRadius: '12px',
                            color: isActive ? 'var(--light-gold)' : 'var(--text-secondary)',
                            background: isActive ? 'rgba(216, 166, 58, 0.1)' : 'transparent',
                            transition: 'all 0.3s ease',
                            border: isActive ? '1px solid var(--border-color)' : '1px solid transparent'
                        }}>
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
    )
}
