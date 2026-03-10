'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Mail, Lock, Loader2 } from 'lucide-react'
import { loginAction } from './actions'

export default function LoginPage() {
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const router = useRouter()

    const handleLogin = async (e) => {
        e.preventDefault()
        setLoading(true)
        setError('')

        const formData = new FormData()
        formData.append('username', username)
        formData.append('password', password)

        try {
            const result = await loginAction(formData)
            if (result.success) {
                router.push('/')
            } else {
                setError(result.message)
            }
        } catch (err) {
            setError('Falha na comunicação com o servidor.')
        } finally {
            setLoading(false)
        }
    }

    return (
        <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
            padding: '24px'
        }}>
            <div className="premium-card animate-fade" style={{ width: '100%', maxWidth: '400px', padding: '40px' }}>
                <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                    <div style={{
                        width: '80px',
                        height: '80px',
                        margin: '0 auto 16px',
                        background: 'var(--primary-red)',
                        borderRadius: '20px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        boxShadow: '0 0 30px var(--primary-red)'
                    }}>
                        <span style={{ fontSize: '32px', fontWeight: '900' }}>X</span>
                    </div>
                    <h1 className="glow-text" style={{ fontSize: '28px', marginBottom: '8px' }}>CineX</h1>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>Painel Control Center</p>
                </div>

                <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    {error && (
                        <div style={{
                            background: 'rgba(178, 30, 43, 0.1)',
                            border: '1px solid var(--primary-red)',
                            color: 'var(--highlight-red)',
                            padding: '12px',
                            borderRadius: '10px',
                            fontSize: '13px',
                            textAlign: 'center'
                        }}>
                            {error}
                        </div>
                    )}

                    <div style={{ position: 'relative' }}>
                        <Mail size={18} style={{ position: 'absolute', left: '16px', top: '14px', color: 'var(--text-muted)' }} />
                        <input
                            type="text"
                            placeholder="Usuário do Painel"
                            className="input-field"
                            style={{ paddingLeft: '48px' }}
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>

                    <div style={{ position: 'relative' }}>
                        <Lock size={18} style={{ position: 'absolute', left: '16px', top: '14px', color: 'var(--text-muted)' }} />
                        <input
                            type="password"
                            placeholder="Senha do Painel"
                            className="input-field"
                            style={{ paddingLeft: '48px' }}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />
                    </div>

                    <button type="submit" className="btn-primary" disabled={loading} style={{
                        marginTop: '10px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '12px'
                    }}>
                        {loading ? <Loader2 className="animate-spin" size={20} /> : 'Entrar no Sistema'}
                    </button>
                </form>

                <p style={{
                    textAlign: 'center',
                    marginTop: '24px',
                    fontSize: '12px',
                    color: 'var(--text-muted)',
                    lineHeight: '1.6'
                }}>
                    Acesso restrito a revendedores oficiais CineX.<br />
                    Seus dados são validados no sistema ADM central.
                </p>
            </div>
        </div>
    )
}
