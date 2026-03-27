'use client'

import { useState, useEffect, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { Plus, Smartphone, Trash2, Edit3, X, Loader2, RefreshCw, Lock, Unlock, Search, Copy, Check, ChevronLeft, ChevronRight, Clock, CalendarDays } from 'lucide-react'
import { getDevices, addDevice, deleteDevice, updateDevice, toggleDeviceStatus } from './actions'

const ITEMS_PER_PAGE = 10

export default function DevicesPage() {
    const [devices, setDevices] = useState([])
    const [loading, setLoading] = useState(true)
    const [isAdding, setIsAdding] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [playlistType, setPlaylistType] = useState('xtream')
    const [macInput, setMacInput] = useState('')
    const [deleting, setDeleting] = useState(null)
    const [deviceToDelete, setDeviceToDelete] = useState(null)
    const [isEditing, setIsEditing] = useState(false)
    const [deviceToEdit, setDeviceToEdit] = useState(null)
    const [editError, setEditError] = useState('')
    const [editSubmitting, setEditSubmitting] = useState(false)
    const [editPlaylistType, setEditPlaylistType] = useState('xtream')
    const [togglingStatus, setTogglingStatus] = useState(null)
    const [deviceToToggle, setDeviceToToggle] = useState(null)
    const [mounted, setMounted] = useState(false)

    // Search & Pagination
    const [searchQuery, setSearchQuery] = useState('')
    const [currentPage, setCurrentPage] = useState(1)
    const [copiedMac, setCopiedMac] = useState(null)

    useEffect(() => {
        const frame = requestAnimationFrame(() => setMounted(true))
        return () => cancelAnimationFrame(frame)
    }, [])

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

    // Filtered devices based on search
    const filteredDevices = devices.filter(device => {
        if (!searchQuery.trim()) return true
        const q = searchQuery.toLowerCase()
        return device.name.toLowerCase().includes(q) || device.mac_address.toLowerCase().includes(q)
    })

    // Pagination
    const totalPages = Math.max(1, Math.ceil(filteredDevices.length / ITEMS_PER_PAGE))
    const safeCurrentPage = Math.min(currentPage, totalPages)
    const paginatedDevices = filteredDevices.slice((safeCurrentPage - 1) * ITEMS_PER_PAGE, safeCurrentPage * ITEMS_PER_PAGE)

    // Reset page when search changes
    useEffect(() => {
        setCurrentPage(1)
    }, [searchQuery])

    // Copy MAC
    const handleCopyMac = async (mac) => {
        try {
            await navigator.clipboard.writeText(mac)
            setCopiedMac(mac)
            setTimeout(() => setCopiedMac(null), 2000)
        } catch {
            // Fallback
            const el = document.createElement('textarea')
            el.value = mac
            document.body.appendChild(el)
            el.select()
            document.execCommand('copy')
            document.body.removeChild(el)
            setCopiedMac(mac)
            setTimeout(() => setCopiedMac(null), 2000)
        }
    }

    // Format date
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

    const handleAdd = async (e) => {
        e.preventDefault()
        setSubmitting(true)
        setError('')

        const formData = new FormData(e.target)
        formData.set('playlist_type', playlistType)
        formData.set('mac', macInput)

        // Validar nome duplicado
        const newName = formData.get('name')?.trim().toLowerCase()
        if (devices.some(d => d.name.trim().toLowerCase() === newName)) {
            setError('Já existe um dispositivo com esse nome. Escolha um nome diferente.')
            setSubmitting(false)
            return
        }

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

    const triggerDelete = (device) => {
        setDeviceToDelete(device)
    }

    const executeDelete = async () => {
        if (!deviceToDelete) return
        const id = deviceToDelete.id
        setDeviceToDelete(null)
        setDeleting(id)

        const result = await deleteDevice(id)
        if (!result.success) {
            alert(result.message || 'Erro ao remover dispositivo.')
        }
        setDeleting(null)
        loadDevices()
    }

    const handleEditClick = (device) => {
        setDeviceToEdit(device)
        setEditPlaylistType(device.playlist_type || 'xtream')
        setIsEditing(true)
        setEditError('')
    }

    const handleUpdate = async (e) => {
        e.preventDefault()
        setEditSubmitting(true)
        setEditError('')

        const formData = new FormData(e.target)
        formData.set('playlist_type', editPlaylistType)

        // Validar nome duplicado (ignorando o próprio dispositivo)
        const newName = formData.get('name')?.trim().toLowerCase()
        if (devices.some(d => d.id !== deviceToEdit.id && d.name.trim().toLowerCase() === newName)) {
            setEditError('Já existe um dispositivo com esse nome. Escolha um nome diferente.')
            setEditSubmitting(false)
            return
        }

        const result = await updateDevice(deviceToEdit.id, formData)
        if (result.success) {
            setIsEditing(false)
            setDeviceToEdit(null)
            loadDevices()
        } else {
            setEditError(result.message)
        }
        setEditSubmitting(false)
    }

    const triggerToggleStatus = (device) => {
        setDeviceToToggle(device)
    }

    const executeToggleStatus = async () => {
        if (!deviceToToggle) return
        const device = deviceToToggle
        const newStatus = device.status === 'Bloqueado' ? 'Ativo' : 'Bloqueado'
        setDeviceToToggle(null)
        setTogglingStatus(device.id)
        const result = await toggleDeviceStatus(device.id, newStatus)
        if (result.success) {
            loadDevices()
        } else {
            alert(result.message || 'Erro ao alterar status.')
        }
        setTogglingStatus(null)
    }

    if (loading) {
        return (
            <div style={{ padding: '40px', textAlign: 'center', paddingTop: '200px', color: 'var(--text-muted)' }}>
                Carregando dispositivos...
            </div>
        )
    }

    return (
        <div style={{ padding: '0' }} className="main-container">
            <style jsx>{`
                .main-container {
                    padding: 40px !important;
                    margin-top: 40px;
                }
                .table-container {
                    width: 100%;
                    overflow-x: auto;
                    -webkit-overflow-scrolling: touch;
                }
                .table-container .premium-card:hover,
                .table-container .premium-card {
                    transform: none !important;
                }
                .search-box {
                    position: relative;
                    flex: 1;
                    max-width: 400px;
                    min-width: 200px;
                }
                .search-box input {
                    width: 100%;
                    padding: 12px 16px 12px 44px;
                    background: rgba(0,0,0,0.2);
                    border: 1px solid var(--glass-border);
                    border-radius: 8px;
                    color: white;
                    font-size: 14px;
                    outline: none;
                    transition: border-color 0.3s ease;
                }
                .search-box input:focus {
                    border-color: var(--premium-gold);
                }
                .search-box input::placeholder {
                    color: var(--text-muted);
                }
                .search-box svg.search-icon {
                    position: absolute;
                    left: 14px;
                    top: 50%;
                    transform: translateY(-50%);
                    pointer-events: none;
                    color: var(--text-muted);
                    z-index: 1;
                }
                .copy-btn {
                    background: none;
                    border: none;
                    cursor: pointer;
                    padding: 4px;
                    border-radius: 4px;
                    display: inline-flex;
                    align-items: center;
                    transition: all 0.2s;
                    vertical-align: middle;
                    margin-left: 8px;
                }
                .copy-btn:hover {
                    background: rgba(216, 166, 58, 0.15);
                }
                .pagination {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                    padding: 20px 0 0;
                }
                .pagination button {
                    background: rgba(0,0,0,0.2);
                    border: 1px solid var(--glass-border);
                    color: var(--text-secondary);
                    border-radius: 8px;
                    padding: 8px 14px;
                    cursor: pointer;
                    font-size: 13px;
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    transition: all 0.2s;
                }
                .pagination button:hover:not(:disabled) {
                    border-color: var(--premium-gold);
                    color: var(--premium-gold);
                }
                .pagination button:disabled {
                    opacity: 0.3;
                    cursor: not-allowed;
                }
                .pagination .page-info {
                    font-size: 13px;
                    color: var(--text-muted);
                    padding: 0 12px;
                }
                .date-info {
                    display: flex;
                    align-items: center;
                    gap: 5px;
                    font-size: 11px;
                    color: var(--text-muted);
                }
                .results-info {
                    font-size: 12px;
                    color: var(--text-muted);
                    padding: 8px 0;
                }
                @media screen and (max-width: 1024px) {
                    .main-container {
                        padding: 20px !important;
                        margin-top: 20px;
                    }
                    .dashboard-header {
                        flex-direction: column;
                        align-items: flex-start !important;
                        gap: 20px;
                    }
                    .header-actions {
                        width: 100%;
                        flex-wrap: wrap;
                    }
                    .header-actions button {
                        flex: 1;
                        min-width: 150px;
                    }
                    .header-title-container h1 {
                        margin-bottom: 8px !important;
                    }
                    .header-title-container p {
                        line-height: 1.4;
                    }
                    .desktop-only-table {
                        display: none !important;
                    }
                    .mobile-only-list {
                        display: flex !important;
                    }
                    .search-box {
                        max-width: 100%;
                        width: 100%;
                    }
                }
            `}</style>
            <header className="dashboard-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <div className="header-title-container">
                    <h1 className="glow-text" style={{ marginBottom: '4px' }}>Gerenciar Dispositivos</h1>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>Vincule listas IPTV aos endereços MAC dos seus clientes</p>
                </div>
                <div className="header-actions" style={{ display: 'flex', gap: '12px' }}>
                    <button onClick={loadDevices} className="input-field" style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                        <RefreshCw size={18} /> Atualizar
                    </button>
                    <button className="btn-primary" onClick={() => setIsAdding(true)} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Plus size={20} /> Adicionar Dispositivo
                    </button>
                </div>
            </header>

            {/* Search Bar */}
            <div style={{ marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
                <div className="search-box">
                    <div className="search-icon" style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none', color: 'var(--text-muted)', zIndex: 1, display: 'flex' }}>
                        <Search size={18} />
                    </div>
                    <input
                        type="text"
                        placeholder="Buscar por nome ou MAC..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                    />
                </div>
                {searchQuery && (
                    <span className="results-info">
                        {filteredDevices.length} resultado{filteredDevices.length !== 1 ? 's' : ''} encontrado{filteredDevices.length !== 1 ? 's' : ''}
                    </span>
                )}
            </div>

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
            ) : filteredDevices.length === 0 ? (
                <div className="premium-card" style={{ padding: '60px', textAlign: 'center' }}>
                    <Search size={40} color="var(--text-muted)" style={{ margin: '0 auto 16px' }} />
                    <h3 style={{ marginBottom: '8px', fontSize: '16px' }}>Nenhum resultado encontrado</h3>
                    <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>
                        Nenhum dispositivo corresponde a &quot;{searchQuery}&quot;
                    </p>
                </div>
            ) : (
                <div className="premium-card table-container" style={{ background: 'transparent', border: 'none', boxShadow: 'none' }}>
                    {/* Desktop Table View */}
                    <div className="desktop-only-table premium-card" style={{ overflow: 'hidden' }}>
                        <table style={{ width: '100%', minWidth: '900px', borderCollapse: 'collapse', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ background: 'rgba(216, 166, 58, 0.1)', color: 'var(--light-gold)', fontSize: '12px', letterSpacing: '1px' }}>
                                    <th style={{ padding: '16px 24px' }}>DISPOSITIVO</th>
                                    <th style={{ padding: '16px 24px' }}>ENDEREÇO MAC</th>
                                    <th style={{ padding: '16px 24px', textAlign: 'center' }}>TIPO</th>
                                    <th style={{ padding: '16px 24px', textAlign: 'center' }}>STATUS</th>
                                    <th style={{ padding: '16px 24px', textAlign: 'center' }}>CRIADO EM</th>
                                    <th style={{ padding: '16px 24px', textAlign: 'center' }}>ÚLTIMO SYNC</th>
                                    <th style={{ padding: '16px 24px', textAlign: 'center' }}>AÇÕES</th>
                                </tr>
                            </thead>
                            <tbody>
                                {paginatedDevices.map(device => (
                                    <tr key={device.id} style={{ borderBottom: '1px solid var(--glass-border)' }}>
                                        <td style={{ padding: '16px 24px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                                <Smartphone size={18} color="var(--premium-gold)" />
                                                {device.name}
                                            </div>
                                        </td>
                                        <td style={{ padding: '16px 24px' }}>
                                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', background: 'rgba(216, 166, 58, 0.08)', border: '1px solid rgba(216, 166, 58, 0.2)', borderRadius: '8px', padding: '6px 12px' }}>
                                                <code style={{ color: 'var(--light-gold)', fontFamily: 'monospace', fontSize: '13px', letterSpacing: '0.5px' }}>{device.mac_address}</code>
                                                <button className="copy-btn" onClick={() => handleCopyMac(device.mac_address)} title="Copiar MAC" style={{ marginLeft: 0 }}>
                                                    {copiedMac === device.mac_address
                                                        ? <Check size={14} style={{ color: '#22c55e' }} />
                                                        : <Copy size={14} style={{ color: 'var(--premium-gold)', opacity: 0.6 }} />
                                                    }
                                                </button>
                                            </div>
                                        </td>
                                        <td style={{ padding: '16px 24px', textAlign: 'center' }}>
                                            <span style={{ fontSize: '12px', background: 'var(--bg-dark)', padding: '4px 10px', borderRadius: '4px', textTransform: 'uppercase' }}>
                                                {device.playlist_type || 'xtream'}
                                            </span>
                                        </td>
                                        <td style={{ padding: '16px 24px', textAlign: 'center' }}>
                                            <div style={{
                                                display: 'inline-flex', alignItems: 'center', gap: '8px',
                                                padding: '4px 12px', borderRadius: '20px',
                                                background: device.status === 'Bloqueado' ? 'rgba(178, 30, 43, 0.1)' : 'rgba(34, 197, 94, 0.1)',
                                                border: `1px solid ${device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e'}`
                                            }}>
                                                <div style={{
                                                    width: '6px', height: '6px', borderRadius: '50%',
                                                    background: device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e',
                                                    boxShadow: `0 0 8px ${device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e'}`
                                                }} />
                                                <span style={{
                                                    fontSize: '12px', fontWeight: '600',
                                                    color: device.status === 'Bloqueado' ? 'var(--highlight-red)' : '#4ade80'
                                                }}>
                                                    {device.status || 'Ativo'}
                                                </span>
                                            </div>
                                        </td>
                                        <td style={{ padding: '16px 24px', textAlign: 'center' }}>
                                            <div className="date-info" style={{ justifyContent: 'center' }}>
                                                <CalendarDays size={13} />
                                                <span>{formatDate(device.created_at)}</span>
                                            </div>
                                        </td>
                                        <td style={{ padding: '16px 24px', textAlign: 'center' }}>
                                            <div className="date-info" style={{ justifyContent: 'center' }}>
                                                <Clock size={13} />
                                                <span>{formatDateTime(device.last_sync)}</span>
                                            </div>
                                        </td>
                                        <td style={{ padding: '16px 24px' }}>
                                            <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
                                                <button
                                                    onClick={() => triggerToggleStatus(device)}
                                                    disabled={togglingStatus === device.id}
                                                    style={{
                                                        background: 'none', border: 'none', cursor: togglingStatus === device.id ? 'wait' : 'pointer',
                                                        padding: '6px', borderRadius: '6px', display: 'flex', alignItems: 'center',
                                                    }}
                                                    title={device.status === 'Bloqueado' ? "Desbloquear dispositivo" : "Bloquear dispositivo"}
                                                >
                                                    {togglingStatus === device.id ? (
                                                        <Loader2 size={18} style={{ color: 'var(--text-muted)', animation: 'spin 1s linear infinite' }} />
                                                    ) : (
                                                        device.status === 'Bloqueado'
                                                            ? <Lock size={18} style={{ color: 'var(--highlight-red)' }} />
                                                            : <Unlock size={18} style={{ color: 'var(--text-secondary)' }} />
                                                    )}
                                                </button>
                                                <button
                                                    onClick={() => handleEditClick(device)}
                                                    style={{
                                                        background: 'none', border: 'none', cursor: 'pointer',
                                                        padding: '6px', borderRadius: '6px', display: 'flex', alignItems: 'center',
                                                    }}
                                                    title="Editar dispositivo"
                                                >
                                                    <Edit3 size={18} style={{ color: 'var(--premium-gold)' }} />
                                                </button>
                                                <button
                                                    onClick={() => triggerDelete(device)}
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

                    {/* Mobile Card View */}
                    <div className="mobile-only-list" style={{ display: 'none', flexDirection: 'column', gap: '16px' }}>
                        {paginatedDevices.map(device => (
                            <div key={device.id} className="premium-card animate-fade" style={{ padding: '20px' }}>
                                <div style={{ marginBottom: '12px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
                                            <Smartphone size={16} color="var(--premium-gold)" />
                                            <span style={{ fontWeight: '700', fontSize: '16px', color: '#ffffff', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{device.name}</span>
                                        </div>
                                        <div style={{
                                            display: 'flex', alignItems: 'center', gap: '6px',
                                            padding: '4px 12px', borderRadius: '12px',
                                            background: device.status === 'Bloqueado' ? 'rgba(178, 30, 43, 0.1)' : 'rgba(34, 197, 94, 0.1)',
                                            border: `1px solid ${device.status === 'Bloqueado' ? 'var(--primary-red)' : '#22c55e'}`,
                                            flexShrink: 0
                                        }}>
                                            <span style={{
                                                fontSize: '10px', fontWeight: '800',
                                                color: device.status === 'Bloqueado' ? 'var(--highlight-red)' : '#4ade80',
                                                textTransform: 'uppercase',
                                                letterSpacing: '0.5px'
                                            }}>
                                                {device.status || 'Ativo'}
                                            </span>
                                        </div>
                                    </div>
                                    <div style={{ paddingLeft: '24px', display: 'flex', alignItems: 'center' }}>
                                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: 'rgba(216, 166, 58, 0.08)', border: '1px solid rgba(216, 166, 58, 0.2)', borderRadius: '8px', padding: '5px 10px' }}>
                                            <code style={{ fontSize: '12px', color: 'var(--light-gold)', fontFamily: 'monospace', letterSpacing: '0.5px' }}>
                                                {device.mac_address}
                                            </code>
                                            <button className="copy-btn" onClick={() => handleCopyMac(device.mac_address)} title="Copiar MAC" style={{ marginLeft: 0 }}>
                                                {copiedMac === device.mac_address
                                                    ? <Check size={13} style={{ color: '#22c55e' }} />
                                                    : <Copy size={13} style={{ color: 'var(--premium-gold)', opacity: 0.6 }} />
                                                }
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                {/* Dates row */}
                                <div style={{ display: 'flex', gap: '16px', paddingLeft: '24px', marginBottom: '12px', flexWrap: 'wrap' }}>
                                    <div className="date-info">
                                        <CalendarDays size={12} />
                                        <span>Criado: {formatDate(device.created_at)}</span>
                                    </div>
                                    <div className="date-info">
                                        <Clock size={12} />
                                        <span>Sync: {formatDateTime(device.last_sync)}</span>
                                    </div>
                                </div>

                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '12px', borderTop: '1px solid var(--glass-border)' }}>
                                    <span style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '1px' }}>
                                        LISTA: <strong style={{ color: 'var(--text-secondary)' }}>{device.playlist_type || 'XTREAM'}</strong>
                                    </span>
                                    <div style={{ display: 'flex', gap: '8px' }}>
                                        <button onClick={() => triggerToggleStatus(device)} disabled={togglingStatus === device.id} className="input-field" style={{ padding: '8px', width: 'auto', display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
                                            {togglingStatus === device.id ? <Loader2 size={16} className="animate-spin" /> : (device.status === 'Bloqueado' ? <Unlock size={16} color="#4ade80" /> : <Lock size={16} color="var(--highlight-red)" />)}
                                        </button>
                                        <button onClick={() => handleEditClick(device)} className="input-field" style={{ padding: '8px', width: 'auto', display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
                                            <Edit3 size={16} color="var(--premium-gold)" />
                                        </button>
                                        <button onClick={() => triggerDelete(device)} className="input-field" style={{ padding: '8px', width: 'auto', display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
                                            <Trash2 size={16} color="var(--primary-red)" />
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>

                    {/* Pagination */}
                    {totalPages > 1 && (
                        <div className="pagination">
                            <button disabled={safeCurrentPage <= 1} onClick={() => setCurrentPage(p => Math.max(1, p - 1))}>
                                <ChevronLeft size={16} /> Anterior
                            </button>
                            <span className="page-info">
                                Página {safeCurrentPage} de {totalPages}
                            </span>
                            <button disabled={safeCurrentPage >= totalPages} onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}>
                                Próxima <ChevronRight size={16} />
                            </button>
                        </div>
                    )}
                </div>
            )}

            {/* Add Device Modal */}
            {mounted && isAdding && createPortal(
                <div style={{
                    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, width: '100vw', height: '100vh',
                    background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999
                }}>
                    <form onSubmit={handleAdd} className="premium-card animate-fade" style={{ width: '550px', maxWidth: '90vw', padding: '32px', margin: 'auto', maxHeight: '90vh', overflowY: 'auto' }}>
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
            , document.body)}

            {/* Edit Device Modal */}
            {mounted && isEditing && deviceToEdit && createPortal(
                <div style={{
                    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, width: '100vw', height: '100vh',
                    background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999
                }}>
                    <form onSubmit={handleUpdate} className="premium-card animate-fade" style={{ width: '550px', maxWidth: '90vw', padding: '32px', margin: 'auto', maxHeight: '90vh', overflowY: 'auto' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                            <h3 className="glow-text">Editar Dispositivo</h3>
                            <X style={{ cursor: 'pointer' }} onClick={() => { setIsEditing(false); setEditError(''); setDeviceToEdit(null) }} />
                        </div>

                        {editError && (
                            <div style={{
                                background: 'rgba(178, 30, 43, 0.1)', border: '1px solid var(--primary-red)',
                                color: 'var(--highlight-red)', padding: '12px', borderRadius: '10px',
                                fontSize: '13px', textAlign: 'center', marginBottom: '16px'
                            }}>
                                {editError}
                            </div>
                        )}

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Nome do Dispositivo</label>
                                <input name="name" className="input-field" defaultValue={deviceToEdit.name} required />
                            </div>
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Endereço MAC (Somente leitura)</label>
                                <input className="input-field" value={deviceToEdit.mac_address} readOnly
                                    style={{ background: 'rgba(0,0,0,0.4)', color: 'var(--text-muted)', fontFamily: 'monospace', letterSpacing: '1px' }}
                                />
                            </div>

                            {/* Playlist Type Selector */}
                            <div>
                                <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Tipo de Lista</label>
                                <div style={{ display: 'flex', gap: '12px' }}>
                                    <button type="button" onClick={() => setEditPlaylistType('xtream')}
                                        style={{
                                            flex: 1, padding: '10px', borderRadius: '10px', cursor: 'pointer',
                                            background: editPlaylistType === 'xtream' ? 'rgba(216, 166, 58, 0.2)' : 'var(--bg-dark)',
                                            border: editPlaylistType === 'xtream' ? '1px solid var(--premium-gold)' : '1px solid var(--glass-border)',
                                            color: editPlaylistType === 'xtream' ? 'var(--premium-gold)' : 'var(--text-secondary)',
                                            fontWeight: editPlaylistType === 'xtream' ? '600' : '400'
                                        }}>
                                        Xtream Codes
                                    </button>
                                    <button type="button" onClick={() => setEditPlaylistType('m3u')}
                                        style={{
                                            flex: 1, padding: '10px', borderRadius: '10px', cursor: 'pointer',
                                            background: editPlaylistType === 'm3u' ? 'rgba(216, 166, 58, 0.2)' : 'var(--bg-dark)',
                                            border: editPlaylistType === 'm3u' ? '1px solid var(--premium-gold)' : '1px solid var(--glass-border)',
                                            color: editPlaylistType === 'm3u' ? 'var(--premium-gold)' : 'var(--text-secondary)',
                                            fontWeight: editPlaylistType === 'm3u' ? '600' : '400'
                                        }}>
                                        M3U URL
                                    </button>
                                </div>
                            </div>

                            {/* Conditional Fields */}
                            {editPlaylistType === 'xtream' ? (
                                <>
                                    <div>
                                        <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>DNS / Servidor</label>
                                        <input name="xtream_dns" className="input-field" defaultValue={deviceToEdit.playlist_config?.dns || ''} placeholder="http://servidor.com:porta" />
                                    </div>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                                        <div>
                                            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Usuário</label>
                                            <input name="xtream_user" className="input-field" defaultValue={deviceToEdit.playlist_config?.user || ''} placeholder="usuario" />
                                        </div>
                                        <div>
                                            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>Senha</label>
                                            <input name="xtream_pass" className="input-field" defaultValue={deviceToEdit.playlist_config?.pass || ''} placeholder="senha" />
                                        </div>
                                    </div>
                                </>
                            ) : (
                                <div>
                                    <label style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', display: 'block' }}>URL da Lista M3U</label>
                                    <input name="m3u_url" className="input-field" defaultValue={deviceToEdit.playlist_config?.url || ''} placeholder="http://exemplo.com/lista.m3u" />
                                </div>
                            )}

                            <button type="submit" className="btn-primary" disabled={editSubmitting} style={{ width: '100%', marginTop: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                                {editSubmitting ? <Loader2 className="animate-spin" size={20} /> : 'Salvar Alterações'}
                            </button>
                        </div>
                    </form>
                </div>
            , document.body)}

            {/* Block/Unblock Confirmation Modal */}
            {mounted && deviceToToggle && createPortal(
                <div style={{
                    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, width: '100vw', height: '100vh',
                    background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999
                }}>
                    <div className="premium-card animate-fade" style={{ width: '400px', maxWidth: '90vw', padding: '32px', textAlign: 'center', margin: 'auto' }}>
                        <div style={{ width: '64px', height: '64px', background: deviceToToggle.status === 'Bloqueado' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(178, 30, 43, 0.1)', border: `1px solid ${deviceToToggle.status === 'Bloqueado' ? 'rgba(34, 197, 94, 0.3)' : 'rgba(178, 30, 43, 0.3)'}`, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
                            {deviceToToggle.status === 'Bloqueado'
                                ? <Unlock size={32} color="#22c55e" />
                                : <Lock size={32} color="var(--primary-red)" />
                            }
                        </div>
                        <h3 style={{ marginBottom: '12px', fontSize: '20px' }}>
                            {deviceToToggle.status === 'Bloqueado' ? 'Desbloquear Dispositivo?' : 'Bloquear Dispositivo?'}
                        </h3>
                        <p style={{ color: 'var(--text-secondary)', marginBottom: '8px', fontSize: '14px' }}>
                            {deviceToToggle.status === 'Bloqueado'
                                ? <>O dispositivo <strong style={{ color: 'var(--text-primary)' }}>{deviceToToggle.name}</strong> voltará a ter acesso à lista IPTV.</>
                                : <>O dispositivo <strong style={{ color: 'var(--text-primary)' }}>{deviceToToggle.name}</strong> perderá o acesso à lista IPTV.</>
                            }
                        </p>
                        <p style={{ color: 'var(--text-muted)', marginBottom: '32px', fontSize: '13px' }}>
                            {deviceToToggle.status === 'Bloqueado'
                                ? 'O cliente poderá acessar o conteúdo normalmente.'
                                : 'O cliente não conseguirá mais acessar o conteúdo.'
                            }
                        </p>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                            <button
                                onClick={() => setDeviceToToggle(null)}
                                className="input-field"
                                style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', padding: '12px' }}
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={executeToggleStatus}
                                className="btn-primary"
                                style={{
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '12px',
                                    background: deviceToToggle.status === 'Bloqueado' ? '#22c55e' : undefined
                                }}
                            >
                                {deviceToToggle.status === 'Bloqueado' ? 'Sim, Desbloquear' : 'Sim, Bloquear'}
                            </button>
                        </div>
                    </div>
                </div>
            , document.body)}

            {/* Delete Confirmation Modal */}
            {mounted && deviceToDelete && createPortal(
                <div style={{
                    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, width: '100vw', height: '100vh',
                    background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999
                }}>
                    <div className="premium-card animate-fade" style={{ width: '400px', maxWidth: '90vw', padding: '32px', textAlign: 'center', margin: 'auto' }}>
                        <div style={{ width: '64px', height: '64px', background: 'rgba(178, 30, 43, 0.1)', border: '1px solid rgba(178, 30, 43, 0.3)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
                            <Trash2 size={32} color="var(--primary-red)" />
                        </div>
                        <h3 style={{ marginBottom: '12px', fontSize: '20px' }}>Remover Dispositivo?</h3>
                        <p style={{ color: 'var(--text-secondary)', marginBottom: '8px', fontSize: '14px' }}>
                            Você está prestes a remover o dispositivo <strong style={{ color: 'var(--text-primary)' }}>{deviceToDelete.name}</strong>.
                        </p>
                        <p style={{ color: 'var(--text-muted)', marginBottom: '32px', fontSize: '13px' }}>
                            Esta ação não pode ser desfeita e ele perderá o acesso à lista IPTV.
                        </p>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                            <button
                                onClick={() => setDeviceToDelete(null)}
                                className="input-field"
                                style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', padding: '12px' }}
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={executeDelete}
                                className="btn-primary"
                                style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '12px' }}
                            >
                                Sim, Remover
                            </button>
                        </div>
                    </div>
                </div>
            , document.body)}
        </div>
    )
}
