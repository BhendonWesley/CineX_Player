'use client'

import { useState } from 'react'
import { Plus, Smartphone, Trash2, Edit3, Save, X } from 'lucide-react'

export default function DevicesPage() {
    const [devices, setDevices] = useState([
        { id: 1, name: 'Salla de Estar', mac: '79:77:0C:0E:46:38', type: 'M3U', status: 'Ativo' },
        { id: 2, name: 'Quarto', mac: 'AA:BB:CC:11:22:33', type: 'Xtream', status: 'Pendente' },
    ])

    const [isAdding, setIsAdding] = useState(false)

    return (
        <div style={{ padding: '40px' }}>
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
                <div>
                    <h1 className="glow-text">Gerenciar Dispositivos</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Vincule listas IPTV aos endereços MAC</p>
                </div>
                <button className="btn-primary" onClick={() => setIsAdding(true)} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Plus size={20} /> Adicionar Dispositivo
                </button>
            </header>

            <div className="premium-card" style={{ overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                        <tr style={{ background: 'rgba(216, 166, 58, 0.1)', color: 'var(--light-gold)' }}>
                            <th style={{ padding: '16px 24px' }}>Dispositivo</th>
                            <th style={{ padding: '16px 24px' }}>Endereço MAC</th>
                            <th style={{ padding: '16px 24px' }}>Tipo</th>
                            <th style={{ padding: '16px 24px' }}>Status</th>
                            <th style={{ padding: '16px 24px' }}>Ações</th>
                        </tr>
                    </thead>
                    <tbody>
                        {devices.map(device => (
                            <tr key={device.id} style={{ borderBottom: '1px solid var(--glass-border)' }}>
                                <td style={{ padding: '16px 24px' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        <Smartphone size={18} color="var(--text-muted)" />
                                        {device.name}
                                    </div>
                                </td>
                                <td style={{ padding: '16px 24px' }}>
                                    <code style={{ color: 'var(--light-gold)', fontFamily: 'monospace' }}>{device.mac}</code>
                                </td>
                                <td style={{ padding: '16px 24px' }}>
                                    <span style={{ fontSize: '12px', background: 'var(--bg-dark)', padding: '4px 8px', borderRadius: '4px' }}>{device.type}</span>
                                </td>
                                <td style={{ padding: '16px 24px' }}>
                                    <span style={{ color: device.status === 'Ativo' ? '#44ff44' : '#ffcc00' }}>● {device.status}</span>
                                </td>
                                <td style={{ padding: '16px 24px' }}>
                                    <div style={{ display: 'flex', gap: '12px' }}>
                                        <Edit3 size={18} style={{ cursor: 'pointer', color: 'var(--text-secondary)' }} />
                                        <Trash2 size={18} style={{ cursor: 'pointer', color: 'var(--primary-red)' }} />
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Basic Add Modal Mock */}
            {isAdding && (
                <div style={{
                    position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
                    background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100
                }}>
                    <div className="premium-card animate-fade" style={{ width: '500px', padding: '32px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                            <h3 className="glow-text">Novo Dispositivo</h3>
                            <X style={{ cursor: 'pointer' }} onClick={() => setIsAdding(false)} />
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Nome do Dispositivo</label>
                                <input className="input-field" placeholder="Ex: TV Quarto" />
                            </div>
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Endereço MAC</label>
                                <input className="input-field" placeholder="00:00:00:00:00:00" />
                            </div>

                            <div style={{ marginTop: '16px' }}>
                                <button className="btn-primary" style={{ width: '100%' }}>Finalizar Registro</button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
