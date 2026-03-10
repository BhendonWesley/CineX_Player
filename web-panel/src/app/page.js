'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'

export default function HomePage() {
  const router = useRouter()

  // For now, redirect to login if not authenticated (static check)
  useEffect(() => {
    // router.push('/login') 
  }, [])

  return (
    <div style={{ padding: '40px' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
        <div>
          <h1 className="glow-text">Dashboard <span style={{ fontSize: '12px', opacity: 0.5 }}>v1.0.1</span></h1>
          <p style={{ color: 'var(--text-secondary)' }}>Visão geral da rede CineX</p>
        </div>
        <div className="premium-card" style={{ padding: '8px 20px', borderRadius: '30px' }}>
          <span style={{ fontSize: '14px', fontWeight: '600' }}>Admin Revenda</span>
        </div>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '24px', marginBottom: '40px' }}>
        {[
          { label: 'Total MACs', value: '1,284', grow: '12%' },
          { label: 'Ativos agora', value: '842', grow: '5%' },
          { label: 'Créditos', value: '450', grow: '-2%' },
          { label: 'Revendedores', value: '24', grow: '10%' },
        ].map((stat, i) => (
          <div key={i} className="premium-card animate-fade" style={{ padding: '24px', animationDelay: `${i * 0.1}s` }}>
            <p style={{ color: 'var(--text-muted)', fontSize: '13px', textTransform: 'uppercase', letterSpacing: '1px' }}>{stat.label}</p>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '10px', marginTop: '12px' }}>
              <h2 style={{ fontSize: '32px' }}>{stat.value}</h2>
              <span style={{ color: stat.grow.startsWith('-') ? '#ff4444' : '#44ff44', fontSize: '12px', fontWeight: 'bold' }}>{stat.grow}</span>
            </div>
          </div>
        ))}
      </div>

      <div className="premium-card animate-fade" style={{ padding: '32px', animationDelay: '0.4s' }}>
        <h3 style={{ marginBottom: '24px' }}>Dispositivos Recentes</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {[1, 2, 3].map(i => (
            <div key={i} style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              paddingBottom: '16px',
              borderBottom: '1px solid var(--glass-border)'
            }}>
              <div>
                <p style={{ fontWeight: '600' }}>TV Samsung QLED</p>
                <code style={{ fontSize: '12px', color: 'var(--premium-gold)' }}>79:77:0C:0E:4{i}:{i}8</code>
              </div>
              <div style={{ textAlign: 'right' }}>
                <p style={{ color: 'var(--text-secondary)', fontSize: '13px' }}>Sincronizado há 2h</p>
                <p style={{ color: '#44ff44', fontSize: '11px', fontWeight: 'bold' }}>ATIVO</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
