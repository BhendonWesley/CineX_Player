'use client'

import { useState, useEffect, useCallback } from 'react'
import { Smartphone, Wifi, WifiOff, User, Shield, CalendarDays, Lock } from 'lucide-react'
import { getDashboardData } from './dashboard-actions'

export default function HomePage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)

  const loadDashboard = useCallback(async () => {
    try {
      setLoading(true)
      const result = await getDashboardData()
      if (result && result.success) {
        setData(result)
      }
    } catch (err) {
      console.error('Failed to load dashboard:', err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  if (loading) {
    return (
      <div style={{ padding: '200px 40px 40px', textAlign: 'center', color: 'var(--text-muted)' }}>
        Carregando dados do painel...
      </div>
    )
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    const d = new Date(dateStr)
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
  }

  const formatDateTime = (dateStr) => {
    if (!dateStr) return 'Nunca sincronizou'
    const d = new Date(dateStr)
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' }) + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  }

  const stats = [
    { label: 'Total de Dispositivos', value: data?.totalDevices || 0, icon: <Smartphone size={22} color="var(--premium-gold)" />, color: 'var(--premium-gold)' },
    { label: 'Dispositivos Ativos', value: data?.activeDevices || 0, icon: <Wifi size={22} color="#22c55e" />, color: '#22c55e' },
    { label: 'Dispositivos Bloqueados', value: data?.blockedDevices || 0, icon: <Lock size={22} color="#ef4444" />, color: '#ef4444' },
  ]

  return (
    <div style={{ padding: '0' }} className="main-container">
      <header className="dashboard-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
        <div>
          <h1 className="glow-text">Dashboard</h1>
          <p style={{ color: 'var(--text-secondary)' }}>Painel do Revendedor CineX</p>
        </div>
        <div className="premium-card" style={{ padding: '8px 20px', borderRadius: '30px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <User size={16} color="var(--premium-gold)" />
          <span style={{ fontSize: '14px', fontWeight: '600' }}>REVENDEDOR CINE X</span>
        </div>
      </header>

      {/* Dashboard Content Wrapper with ordering logic in CSS */}
      <div className="dashboard-content">
        {/* Welcome Card */}
        <div className="premium-card animate-fade" style={{ padding: '32px', borderLeft: '4px solid var(--primary-red)', order: 0 }}>
          <h2 style={{ fontSize: '22px', marginBottom: '8px' }}>
            Olá, <span style={{ color: 'var(--premium-gold)' }}>{data?.username || 'Revendedor'}</span>! 👋
          </h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
            Este painel foi criado para que você possa gerenciar seus dispositivos e enviar listas IPTV diretamente para o aplicativo <strong>CineX Player</strong> dos seus clientes.
          </p>
        </div>

        {/* Recent Devices (First on mobile via order) */}
        <div className="recent-container premium-card animate-fade" style={{ padding: '32px', animationDelay: '0.3s' }}>
          <h3 style={{ marginBottom: '24px' }}>Dispositivos Recentes</h3>
          {data?.recentDevices && data.recentDevices.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {data.recentDevices.map((device, i) => (
                <div key={i} style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  paddingBottom: '16px',
                  borderBottom: '1px solid var(--glass-border)',
                  gap: '12px'
                }}>
                  <div style={{ minWidth: 0 }}>
                    <p style={{ fontWeight: '600', marginBottom: '4px' }}>{device.name}</p>
                    <code style={{ fontSize: '12px', color: 'var(--premium-gold)' }}>{device.mac_address}</code>
                    <div style={{ display: 'flex', gap: '16px', marginTop: '6px', flexWrap: 'wrap' }}>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <CalendarDays size={11} /> {formatDate(device.created_at)}
                      </span>
                      {device.last_sync && (
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Shield size={11} /> Sync: {formatDateTime(device.last_sync)}
                        </span>
                      )}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right', flexShrink: 0 }}>
                    <div style={{
                      display: 'inline-flex', alignItems: 'center', gap: '6px',
                      padding: '4px 10px', borderRadius: '16px',
                      background: device.status === 'Bloqueado' ? 'rgba(178, 30, 43, 0.1)' : 'rgba(34, 197, 94, 0.1)',
                      border: `1px solid ${device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e'}`
                    }}>
                      <div style={{
                        width: '5px', height: '5px', borderRadius: '50%',
                        background: device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e',
                        boxShadow: `0 0 6px ${device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e'}`
                      }} />
                      <span style={{
                        fontSize: '11px', fontWeight: '600',
                        color: device.status === 'Bloqueado' ? 'var(--highlight-red)' : '#4ade80'
                      }}>
                        {device.status || 'Ativo'}
                      </span>
                    </div>
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

        {/* Stats (Second on mobile via order) */}
        <div className="stats-container dashboard-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px' }}>
          {stats.map((stat, i) => (
            <div key={i} className="premium-card animate-fade" style={{ padding: '24px', animationDelay: `${i * 0.1}s` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
                {stat.icon}
                <p style={{ color: '#ffffff', fontSize: '13px', textTransform: 'uppercase', letterSpacing: '1px' }}>{stat.label}</p>
              </div>
              <h2 style={{ fontSize: '32px', color: stat.color }}>{stat.value}</h2>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
