'use client'

'use client'

import { useState, useEffect, useCallback } from 'react'
import { Users, UserPlus, Shield, Activity, RefreshCw } from 'lucide-react'
import { fetchResellersAction } from './actions'

export default function ResellersPage() {
    const [resellers, setResellers] = useState([])
    const [loading, setLoading] = useState(true)

    const loadData = useCallback(async () => {
        // We initialize loading to true in the state, so we only need to set it to false when done
        const result = await fetchResellersAction()
        if (result.success) {
            setResellers(result.resellers)
        }
        setLoading(false)
    }, [])

    useEffect(() => {
        loadData()
    }, [loadData])

    return (
        <div style={{ padding: '40px' }}>
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
                <div>
                    <h1 className="glow-text">Rede de Revendedores</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Gerencie permissões e monitore atividades sincronizadas do ADM</p>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <button onClick={loadData} className="input-field" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <RefreshCw size={18} className={loading ? 'animate-spin' : ''} /> Atualizar
                    </button>
                    <button className="btn-primary" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <UserPlus size={20} /> Novo Revendedor
                    </button>
                </div>
            </header>

            {loading && resellers.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '100px', color: 'var(--text-muted)' }}>Carregando dados do painel ADM...</div>
            ) : (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                    {resellers.map(reseller => (
                        <div key={reseller.id} className="premium-card animate-fade" style={{ padding: '24px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                    <div style={{ padding: '10px', background: 'rgba(216, 166, 58, 0.1)', borderRadius: '10px' }}>
                                        <Shield size={24} color="var(--premium-gold)" />
                                    </div>
                                    <div>
                                        <h3 style={{ fontSize: '18px' }}>{reseller.username}</h3>
                                        <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>{reseller.email}</p>
                                    </div>
                                </div>
                                <div dangerouslySetInnerHTML={{ __html: reseller.status }} />
                            </div>

                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', background: 'rgba(0,0,0,0.2)', padding: '16px', borderRadius: '12px' }}>
                                <div>
                                    <p style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Créditos</p>
                                    <p style={{ fontSize: '20px', fontWeight: 'bold' }}>{reseller.credits}</p>
                                </div>
                                <div>
                                    <p style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Grupo</p>
                                    <div dangerouslySetInnerHTML={{ __html: reseller.group }} />
                                </div>
                            </div>

                            <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
                                <button className="input-field" style={{ flex: 1, padding: '8px', fontSize: '13px' }}>Editar Perfil</button>
                                <button className="input-field" style={{ flex: 1, padding: '8px', fontSize: '13px' }}>Ver MACs</button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}
