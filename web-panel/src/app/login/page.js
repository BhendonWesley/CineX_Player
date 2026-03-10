'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, User, Loader2, Shield, Smartphone, Zap } from 'lucide-react'
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

    const features = [
        { icon: <Smartphone size={20} />, text: 'Gerencie dispositivos via MAC' },
        { icon: <Zap size={20} />, text: 'Envio de listas M3U e Xtream' },
        { icon: <Shield size={20} />, text: 'Sincronização com CineX Player' },
    ]

    return (
        <div style={{
            display: 'flex',
            minHeight: '100vh',
            background: 'linear-gradient(135deg, #0B0F1A 0%, #1A1A1A 50%, #0B0F1A 100%)',
            position: 'relative',
            overflow: 'hidden',
        }}>
            {/* Cinematic Background Glow */}
            <div style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                width: '800px',
                height: '800px',
                background: 'radial-gradient(circle, rgba(178,30,43,0.12) 0%, transparent 70%)',
                pointerEvents: 'none',
            }} />
            <div style={{
                position: 'absolute',
                top: '20%',
                right: '15%',
                width: '400px',
                height: '400px',
                background: 'radial-gradient(circle, rgba(216,166,58,0.06) 0%, transparent 70%)',
                pointerEvents: 'none',
            }} />

            {/* LEFT SIDE - Brand Identity */}
            <div style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                padding: '60px',
                position: 'relative',
                zIndex: 1,
            }}>
                <div style={{ textAlign: 'center', maxWidth: '480px' }}>
                    {/* Logo */}
                    <div style={{
                        marginBottom: '32px',
                        filter: 'drop-shadow(0 0 40px rgba(178,30,43,0.4))',
                    }}>
                        <img
                            src="/logo_cinex.png"
                            alt="CineX Logo"
                            width={220}
                            height={220}
                            style={{ objectFit: 'contain' }}
                        />
                    </div>

                    <h1 style={{
                        fontSize: '36px',
                        fontWeight: '800',
                        color: '#FFFFFF',
                        marginBottom: '8px',
                        letterSpacing: '-0.5px',
                    }}>
                        Control Center
                    </h1>

                    <p style={{
                        fontSize: '16px',
                        color: '#D8A63A',
                        fontWeight: '600',
                        marginBottom: '32px',
                        letterSpacing: '2px',
                        textTransform: 'uppercase',
                    }}>
                        Painel Oficial de Revendedores
                    </p>

                    <p style={{
                        fontSize: '15px',
                        color: '#A0A7B5',
                        lineHeight: '1.7',
                        marginBottom: '48px',
                    }}>
                        Ative dispositivos CineX Player para seus clientes com rapidez e segurança.
                    </p>

                    {/* Feature List */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        {features.map((f, i) => (
                            <div key={i} style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '14px',
                                padding: '12px 20px',
                                background: 'rgba(255,255,255,0.03)',
                                borderRadius: '12px',
                                border: '1px solid rgba(216,166,58,0.08)',
                            }}>
                                <div style={{ color: '#D8A63A' }}>{f.icon}</div>
                                <span style={{ color: '#A0A7B5', fontSize: '14px' }}>{f.text}</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* RIGHT SIDE - Login Card */}
            <div style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '60px',
                position: 'relative',
                zIndex: 1,
            }}>
                <div style={{
                    width: '100%',
                    maxWidth: '420px',
                    background: 'linear-gradient(145deg, rgba(26,26,26,0.95), rgba(15,15,25,0.95))',
                    borderRadius: '14px',
                    border: '1px solid rgba(216,166,58,0.12)',
                    padding: '48px 40px',
                    boxShadow: '0 20px 60px rgba(0,0,0,0.5), 0 0 80px rgba(178,30,43,0.08)',
                    backdropFilter: 'blur(20px)',
                }}>
                    {/* Card Logo */}
                    <div style={{ textAlign: 'center', marginBottom: '36px' }}>
                        <div style={{
                            width: '64px', height: '64px',
                            margin: '0 auto 20px',
                            background: 'linear-gradient(135deg, #B21E2B, #8E0F1E)',
                            borderRadius: '16px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 4px 20px rgba(178,30,43,0.4)',
                        }}>
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img
                                src="/logo_cinex.png"
                                alt="CineX"
                                width={48}
                                height={48}
                                style={{ objectFit: 'contain' }}
                            />
                        </div>
                        <h2 style={{
                            fontSize: '22px',
                            fontWeight: '700',
                            color: '#FFFFFF',
                            marginBottom: '4px',
                        }}>
                            Acesso ao Painel
                        </h2>
                        <p style={{ fontSize: '13px', color: '#A0A7B5' }}>
                            Autenticação via sistema CineX TV
                        </p>
                    </div>

                    <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                        {error && (
                            <div style={{
                                background: 'rgba(178, 30, 43, 0.1)',
                                border: '1px solid rgba(178, 30, 43, 0.3)',
                                color: '#E23A3A',
                                padding: '12px 16px',
                                borderRadius: '10px',
                                fontSize: '13px',
                                textAlign: 'center',
                            }}>
                                {error}
                            </div>
                        )}

                        <div style={{ position: 'relative' }}>
                            <User size={18} style={{
                                position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)',
                                color: '#A0A7B5', pointerEvents: 'none',
                            }} />
                            <input
                                type="text"
                                placeholder="Usuário do Painel"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                required
                                style={{
                                    width: '100%',
                                    padding: '14px 16px 14px 48px',
                                    background: 'rgba(11,15,26,0.8)',
                                    border: '1px solid rgba(216,166,58,0.1)',
                                    borderRadius: '10px',
                                    color: '#FFFFFF',
                                    fontSize: '14px',
                                    outline: 'none',
                                    transition: 'border-color 0.3s ease, box-shadow 0.3s ease',
                                    boxSizing: 'border-box',
                                }}
                                onFocus={(e) => {
                                    e.target.style.borderColor = 'rgba(216,166,58,0.4)'
                                    e.target.style.boxShadow = '0 0 20px rgba(216,166,58,0.08)'
                                }}
                                onBlur={(e) => {
                                    e.target.style.borderColor = 'rgba(216,166,58,0.1)'
                                    e.target.style.boxShadow = 'none'
                                }}
                            />
                        </div>

                        <div style={{ position: 'relative' }}>
                            <Lock size={18} style={{
                                position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)',
                                color: '#A0A7B5', pointerEvents: 'none',
                            }} />
                            <input
                                type="password"
                                placeholder="Senha do Painel"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                                style={{
                                    width: '100%',
                                    padding: '14px 16px 14px 48px',
                                    background: 'rgba(11,15,26,0.8)',
                                    border: '1px solid rgba(216,166,58,0.1)',
                                    borderRadius: '10px',
                                    color: '#FFFFFF',
                                    fontSize: '14px',
                                    outline: 'none',
                                    transition: 'border-color 0.3s ease, box-shadow 0.3s ease',
                                    boxSizing: 'border-box',
                                }}
                                onFocus={(e) => {
                                    e.target.style.borderColor = 'rgba(216,166,58,0.4)'
                                    e.target.style.boxShadow = '0 0 20px rgba(216,166,58,0.08)'
                                }}
                                onBlur={(e) => {
                                    e.target.style.borderColor = 'rgba(216,166,58,0.1)'
                                    e.target.style.boxShadow = 'none'
                                }}
                            />
                        </div>

                        <button type="submit" disabled={loading} style={{
                            marginTop: '8px',
                            padding: '14px',
                            background: loading ? 'rgba(178,30,43,0.5)' : 'linear-gradient(135deg, #B21E2B, #E23A3A)',
                            border: 'none',
                            borderRadius: '10px',
                            color: '#FFFFFF',
                            fontSize: '15px',
                            fontWeight: '600',
                            cursor: loading ? 'not-allowed' : 'pointer',
                            transition: 'all 0.3s ease',
                            boxShadow: '0 4px 20px rgba(178,30,43,0.3)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '10px',
                        }}>
                            {loading ? <Loader2 className="animate-spin" size={20} /> : 'Entrar no Painel'}
                        </button>
                    </form>

                    <p style={{
                        textAlign: 'center',
                        marginTop: '28px',
                        fontSize: '12px',
                        color: '#A0A7B5',
                        lineHeight: '1.6',
                    }}>
                        Painel exclusivo para revendedores oficiais CineX.
                    </p>
                </div>
            </div>
        </div>
    )
}
