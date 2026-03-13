'use client'

import { useState, useEffect, useCallback } from 'react'
import { Smartphone, Wifi, WifiOff, User } from 'lucide-react'
import { getDashboardData } from './dashboard-actions'

export default function HomePage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)

  const loadDashboard = useCallback(async () => {
    const result = await getDashboardData()
    if (result.success) {
      setData(result)
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadDashboard()
  }, [loadDashboard])

  if (loading) {
    return (
      <div style={{ padding: '200px 40px 40px', textAlign: 'center', color: 'var(--text-muted)' }}>
        Carregando dados do painel...
      </div>
    )
  }

  const stats = [
    { label: 'Total Dispositivos', value: data?.totalDevices || 0, icon: <Smartphone size={20} color="var(--premium-gold)" /> },
    { label: 'Ativos', value: data?.activeDevices || 0, icon: <Wifi size={20} color="#44ff44" /> },
    { label: 'Inativos', value: data?.inactiveDevices || 0, icon: <WifiOff size={20} color="#ff4444" /> },
  ]

  return (
    <div style={{ padding: '40px' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
        <div>
          <h1 className="glow-text">Dashboard</h1>
          <p style={{ color: 'var(--text-secondary)' }}>Painel do Revendedor CineX</p>
        </div>
        <div className="premium-card" style={{ padding: '8px 20px', borderRadius: '30px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <User size={16} color="var(--premium-gold)" />
          <span style={{ fontSize: '14px', fontWeight: '600' }}>REVENDEDOR CINE X</span>
        </div>
      </header>

      {/* Welcome Card */}
      <div className="premium-card animate-fade" style={{ padding: '32px', marginBottom: '32px', borderLeft: '4px solid var(--primary-red)' }}>
        <h2 style={{ fontSize: '22px', marginBottom: '8px' }}>
          Olá, <span style={{ color: 'var(--premium-gold)' }}>{data?.username || 'Revendedor'}</span>! 👋
        </h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
          Este painel foi criado para que você possa gerenciar seus dispositivos e enviar listas IPTV diretamente para o aplicativo <strong>CineX Player</strong> dos seus clientes.
        </p>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px', marginBottom: '40px' }}>
        {stats.map((stat, i) => (
          <div key={i} className="premium-card animate-fade" style={{ padding: '24px', animationDelay: `${i * 0.1}s` }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
              {stat.icon}
              <p style={{ color: '#ffffff', fontSize: '13px', textTransform: 'uppercase', letterSpacing: '1px' }}>{stat.label}</p>
            </div>
            <h2 style={{ fontSize: '32px', color: '#ffffff' }}>{stat.value}</h2>
          </div>
        ))}
      </div>

      {/* Recent Devices */}
      <div className="premium-card animate-fade" style={{ padding: '32px', animationDelay: '0.3s' }}>
        <h3 style={{ marginBottom: '24px' }}>Dispositivos Recentes</h3>
        {data?.recentDevices && data.recentDevices.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {data.recentDevices.map((device, i) => (
              <div key={i} style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '16px',
                borderBottom: '1px solid var(--glass-border)'
              }}>
                <div>
                  <p style={{ fontWeight: '600' }}>{device.name}</p>
                  <code style={{ fontSize: '12px', color: 'var(--premium-gold)' }}>{device.mac_address}</code>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <p style={{ color: device.status === 'Ativo' ? '#44ff44' : '#ff4444', fontSize: '12px', fontWeight: 'bold' }}>
                    ● {device.status || 'Ativo'}
                  </p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '40px' }}>
            Nenhum dispositivo cadastrado ainda. Vá em Dispositivos para adicionar.
          </p>
        )}
      </div>
    </div>
  )
}
