'use client'

import { useState, useEffect, useCallback } from 'react'
import { Plus, Smartphone, Trash2, Edit3, X, Loader2, RefreshCw } from 'lucide-react'
import { getDevices, addDevice, deleteDevice } from './actions'

export default function DevicesPage() {
    const [devices, setDevices] = useState([])
    const [loading, setLoading] = useState(true)
    const [isAdding, setIsAdding] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [playlistType, setPlaylistType] = useState('xtream')
    const [macInput, setMacInput] = useState('')
    const [deleting, setDeleting] = useState(null)

    // Auto-format MAC: uppercase, hex only, ":" separator
    const formatMac = (value) => {
        const hex = value.replace(/[^a-fA-F0-9]/g, '').toUpperCase().slice(0, 12)
        const parts = hex.match(/.{1,2}/g) || []
        return parts.join(':')
    }

    const handleMacChange = (e) => {
        setMacInput(formatMac(e.target.value))
    }

    const loadDevices = useCallback(async () => {
        const result = await getDevices()
        if (result.success) {
            setDevices(result.devices)
        }
        setLoading(false)
    }, [])

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadDevices()
    }, [loadDevices])

    const handleAdd = async (e) => {
        e.preventDefault()
        setSubmitting(true)
        setError('')

        const formData = new FormData(e.target)
        formData.set('playlist_type', playlistType)
        formData.set('mac', macInput) // Use formatted MAC

        const result = await addDevice(formData)
        if (result.success) {
            setIsAdding(false)
            setPlaylistType('xtream')
            setMacInput('')
            loadDevices()
        } else {
            setError(result.message)
        }
        setSubmitting(false)
    }

    const handleDelete = async (id) => {
        if (!confirm('Tem certeza que deseja remover este dispositivo?')) return
        setDeleting(id)
        const result = await deleteDevice(id)
        if (!result.success) {
            alert(result.message || 'Erro ao remover dispositivo.')
        }
        setDeleting(null)
        loadDevices()
    }

    if (loading) {
        return (
            <div style={{ padding: '40px', textAlign: 'center', paddingTop: '200px', color: 'var(--text-muted)' }}>
                Carregando dispositivos...
            </div>
        )
    }

    return (
        <div style={{ padding: '40px' }}>
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
                <div>
                    <h1 className="glow-text">Gerenciar Dispositivos</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Vincule listas IPTV aos endereços MAC dos seus clientes</p>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <button onClick={loadDevices} className="input-field" style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                        <RefreshCw size={18} /> Atualizar
                    </button>
                    <button className="btn-primary" onClick={() => setIsAdding(true)} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={20} /> Adicionar Dispositivo
                    </button>
                </div>
            </header>

            {devices.length === 0 ? (
                <div className="premium-card" style={{ padding: '80px', textAlign: 'center' }}>
                    <Smartphone size={48} color="var(--text-muted)" style={{ margin: '0 auto 16px' }} />
                    <h3 style={{ marginBottom: '8px' }}>Nenhum dispositivo cadastrado</h3>
                    <p style={{ color: 'var(--text-muted)', marginBottom: '24px' }}>
                        Cadastre o MAC do cliente e vincule a lista IPTV para ele acessar pelo app.
                    </p>
                    <button className="btn-primary" onClick={() => setIsAdding(true)} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={20} /> Cadastrar Primeiro Dispositivo
                    </button>
                </div>
            ) : (
                <div className="premium-card" style={{ overflow: 'hidden' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead>
                            <tr style={{ background: 'rgba(216, 166, 58, 0.1)', color: 'var(--light-gold)' }}>
                                <th style={{ padding: '16px 24px' }}>Dispositivo</th>
                                <th style={{ padding: '16px 24px' }}>Endereço MAC</th>
                                <th style={{ padding: '16px 24px' }}>Tipo Lista</th>
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
                                        <code style={{ color: 'var(--light-gold)', fontFamily: 'monospace' }}>{device.mac_address}</code>
                                    </td>
                                    <td style={{ padding: '16px 24px' }}>
                                        <span style={{ fontSize: '12px', background: 'var(--bg-dark)', padding: '4px 10px', borderRadius: '4px', textTransform: 'uppercase' }}>
                                            {device.playlist_type || 'xtream'}
                                        </span>
                                    </td>
                                    <td style={{ padding: '16px 24px' }}>
                                        <span style={{ color: device.status === 'Ativo' ? '#44ff44' : '#ffcc00' }}>● {device.status}</span>
                                    </td>
                                    <td style={{ padding: '16px 24px' }}>
                                        <div style={{ display: 'flex', gap: '12px' }}>
                                            <button
                                                onClick={() => handleDelete(device.id)}
                                                disabled={deleting === device.id}
                                                style={{
                                                    background: 'none', border: 'none', cursor: deleting === device.id ? 'wait' : 'pointer',
                                                    padding: '6px', borderRadius: '6px', display: 'flex', alignItems: 'center',
                                                }}
                                                title="Remover dispositivo"
                                            >
                                                {deleting === device.id
                                                    ? <Loader2 size={18} style={{ color: 'var(--primary-red)', animation: 'spin 1s linear infinite' }} />
                                                    : <Trash2 size={18} style={{ color: 'var(--primary-red)' }} />
                                                }
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Add Device Modal */}
            {isAdding && (
                <div style={{
                    position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
                    background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100
                }}>
                    <form onSubmit={handleAdd} className="premium-card animate-fade" style={{ width: '550px', padding: '32px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                            <h3 className="glow-text">Novo Dispositivo</h3>
                            <X style={{ cursor: 'pointer' }} onClick={() => { setIsAdding(false); setError(''); setMacInput('') }} />
                        </div>

                        {error && (
                            <div style={{
                                background: 'rgba(178, 30, 43, 0.1)', border: '1px solid var(--primary-red)',
                                color: 'var(--highlight-red)', padding: '12px', borderRadius: '10px',
                                fontSize: '13px', textAlign: 'center', marginBottom: '16px'
                            }}>
                                {error}
                            </div>
                        )}

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Nome do Dispositivo</label>
                                <input name="name" className="input-field" placeholder="Ex: TV do João" required />
                            </div>
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Endereço MAC do Cliente</label>
                                <input name="mac" className="input-field" placeholder="00:1A:2B:3C:4D:5E" required
                                    value={macInput}
                                    onChange={handleMacChange}
                                    maxLength={17}
                                    style={{ textTransform: 'uppercase', fontFamily: 'monospace', letterSpacing: '1px' }}
                                />
                            </div>

                            {/* Playlist Type Selector */}
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Tipo de Lista</label>
                                <div style={{ display: 'flex', gap: '12px' }}>
                                    <button type="button" onClick={() => setPlaylistType('xtream')}
                                        style={{
                                            flex: 1, padding: '10px', borderRadius: '10px', cursor: 'pointer',
                                            background: playlistType === 'xtream' ? 'rgba(216, 166, 58, 0.2)' : 'var(--bg-dark)',
                                            border: playlistType === 'xtream' ? '1px solid var(--premium-gold)' : '1px solid var(--glass-border)',
                                            color: playlistType === 'xtream' ? 'var(--premium-gold)' : 'var(--text-secondary)',
                                            fontWeight: playlistType === 'xtream' ? '600' : '400'
                                        }}>
                                        Xtream Codes
                                    </button>
                                    <button type="button" onClick={() => setPlaylistType('m3u')}
                                        style={{
                                            flex: 1, padding: '10px', borderRadius: '10px', cursor: 'pointer',
                                            background: playlistType === 'm3u' ? 'rgba(216, 166, 58, 0.2)' : 'var(--bg-dark)',
                                            border: playlistType === 'm3u' ? '1px solid var(--premium-gold)' : '1px solid var(--glass-border)',
                                            color: playlistType === 'm3u' ? 'var(--premium-gold)' : 'var(--text-secondary)',
                                            fontWeight: playlistType === 'm3u' ? '600' : '400'
                                        }}>
                                        M3U URL
                                    </button>
                                </div>
                            </div>

                            {/* Conditional Fields */}
                            {playlistType === 'xtream' ? (
                                <>
                                    <div>
                                        <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>DNS / Servidor</label>
                                        <input name="xtream_dns" className="input-field" placeholder="http://servidor.com:porta" />
                                    </div>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                                        <div>
                                            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Usuário</label>
                                            <input name="xtream_user" className="input-field" placeholder="usuario" />
                                        </div>
                                        <div>
                                            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Senha</label>
                                            <input name="xtream_pass" className="input-field" placeholder="senha" />
                                        </div>
                                    </div>
                                </>
                            ) : (
                                <div>
                                    <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>URL da Lista M3U</label>
                                    <input name="m3u_url" className="input-field" placeholder="http://exemplo.com/lista.m3u" />
                                </div>
                            )}

                            <button type="submit" className="btn-primary" disabled={submitting} style={{ width: '100%', marginTop: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                                {submitting ? <Loader2 className="animate-spin" size={20} /> : 'Cadastrar Dispositivo'}
                            </button>
                        </div>
                    </form>
                </div>
            )}
        </div>
    )
}
