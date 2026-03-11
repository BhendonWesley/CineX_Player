'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, User, Loader2, Shield, Smartphone, Zap } from 'lucide-react'
import { loginAction } from './actions'

// ═══════════════════════════════════════════════════════
//  TMDB POSTER URLs — Real Movie Posters for Poster Wall
// ═══════════════════════════════════════════════════════
const TMDB_BASE = 'https://image.tmdb.org/t/p/w500'
const POSTERS = [
    // Column 1 (Large - Slow)
    [`${TMDB_BASE}/pB8BM7pdSp6B6Ih7QI4S2t0POoT.jpg`, `${TMDB_BASE}/d5NXSklXo0qyIYkgV94XAgMIckC.jpg`, `${TMDB_BASE}/z1p34vh7dEOnLDV4jC1DGcKSqRm.jpg`, `${TMDB_BASE}/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg`, `${TMDB_BASE}/ngl2FKBlU4fhbdsrtdom9LVLBXw.jpg`, `${TMDB_BASE}/qJ2tW6WMUDux911SbMqGMYV4f3R.jpg`],
    // Column 2 (Medium - Medium)
    [`${TMDB_BASE}/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg`, `${TMDB_BASE}/7WsyChQLEftFiDhRDpZFHSNbCYc.jpg`, `${TMDB_BASE}/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg`, `${TMDB_BASE}/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg`, `${TMDB_BASE}/ldfCF9RhR40l1UkalGw99cHVmhY.jpg`, `${TMDB_BASE}/sv1xJUazXeYqAyy0lB4w4Q3Rjya.jpg`],
    // Column 3 (Small - Fast)
    [`${TMDB_BASE}/uS1AIL7I1Ycgs8PTfqUeN6jYNsQ.jpg`, `${TMDB_BASE}/sF1U4EUQS8YMrncNmIk3LaZIbZk.jpg`, `${TMDB_BASE}/bx7J7oAGHW8FPJCXxCaJYgSBORL.jpg`, `${TMDB_BASE}/AkNiKuRLPfj1XXwXNlXjNXPq6n3.jpg`, `${TMDB_BASE}/pnXLFioDeftqjlCVlRmXvIdMsdh.jpg`, `${TMDB_BASE}/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg`],
    // Column 4 (Large - Slow reverse)
    [`${TMDB_BASE}/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg`, `${TMDB_BASE}/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg`, `${TMDB_BASE}/pIkGQo1lhmGn8MIKHMRhJoHT0y2.jpg`, `${TMDB_BASE}/6CoRTJTmijhBLJTUNoVSUNxZMEI.jpg`, `${TMDB_BASE}/ym1dxyOk4jFcSvhGlWYee3Te1Kc.jpg`, `${TMDB_BASE}/dqK9Hag1054tghRQSqLSfrkvQnA.jpg`],
    // Column 5 (Medium - Medium reverse)
    [`${TMDB_BASE}/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg`, `${TMDB_BASE}/NNxYkU70HPurnNCSiCjYAmacwm.jpg`, `${TMDB_BASE}/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg`, `${TMDB_BASE}/gPbM0MK8CP8A174kSwuaRnJKKrM.jpg`, `${TMDB_BASE}/8b8R8l88Qje9dn9OE8PY05Nez7H.jpg`, `${TMDB_BASE}/hu40Uj7mEQR2LgRhroQMlXfOdjr.jpg`],
    // Column 6 (Small - Fast reverse)
    [`${TMDB_BASE}/vpnVM9B6NMmQpWeZvzLvDESb2gY.jpg`, `${TMDB_BASE}/1E5baAaEse26fej7uHcjOgEERB2.jpg`, `${TMDB_BASE}/4m1Au3YkjqsxF8iwQy0fPYSxE0h.jpg`, `${TMDB_BASE}/49WJfeN0moxb9IPfGn8AIqMGskD.jpg`, `${TMDB_BASE}/5gzzkR7y3qqY8BLYnSA7o0vqqzU.jpg`, `${TMDB_BASE}/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg`],
]

const COL_CONFIG = [
    { size: 'poster-large',  speed: 'poster-col-slow' },
    { size: 'poster-medium', speed: 'poster-col-medium' },
    { size: 'poster-small',  speed: 'poster-col-fast' },
    { size: 'poster-large',  speed: 'poster-col-medium' },
    { size: 'poster-medium', speed: 'poster-col-slow' },
    { size: 'poster-small',  speed: 'poster-col-fast' },
]

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
            minHeight: '100vh',
            background: '#0B0F1A',
            overflow: 'hidden',
        }}>

            {/* ═══ LEFT SIDE — POSTER WALL (60%) ═══ */}
            <div style={{
                flex: '0 0 60%',
                position: 'relative',
                overflow: 'hidden',
            }}>
                {/* Poster Grid */}
                <div style={{
                    display: 'flex',
                    gap: '12px',
                    padding: '0 20px',
                    height: '100vh',
                    alignItems: 'flex-start',
                }}>
                    {POSTERS.map((col, colIndex) => {
                        const config = COL_CONFIG[colIndex]
                        // Duplicate posters for seamless infinite loop
                        const doubled = [...col, ...col]
                        return (
                            <div
                                key={colIndex}
                                className={`poster-col ${config.speed}`}
                                style={{ paddingTop: colIndex % 2 === 0 ? '0' : '60px' }}
                            >
                                {doubled.map((url, i) => (
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img
                                        key={i}
                                        src={url}
                                        alt=""
                                        className={`poster-img ${config.size}`}
                                        loading="lazy"
                                    />
                                ))}
                            </div>
                        )
                    })}
                </div>

                {/* Dark Gradient Overlay */}
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    background: `linear-gradient(
                        90deg,
                        rgba(11,15,26,0.4) 0%,
                        rgba(11,15,26,0.55) 50%,
                        rgba(11,15,26,0.95) 100%
                    )`,
                    pointerEvents: 'none',
                }} />
                {/* Vertical gradient */}
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    background: `linear-gradient(
                        180deg,
                        rgba(11,15,26,0.7) 0%,
                        rgba(11,15,26,0.2) 30%,
                        rgba(11,15,26,0.2) 70%,
                        rgba(11,15,26,0.8) 100%
                    )`,
                    pointerEvents: 'none',
                }} />

                {/* Brand Content Overlay */}
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    padding: '60px 80px',
                    zIndex: 2,
                }}>
                    {/* Logo */}
                    <div style={{ marginBottom: '24px' }}>
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                            src="/logo_cinex.png"
                            alt="CineX"
                            width={90}
                            height={90}
                            style={{ objectFit: 'contain', filter: 'drop-shadow(0 0 30px rgba(178,30,43,0.5))' }}
                        />
                    </div>

                    <h1 style={{
                        fontSize: '42px',
                        fontWeight: '800',
                        color: '#FFFFFF',
                        marginBottom: '4px',
                        letterSpacing: '-1px',
                        lineHeight: 1.1,
                    }}>
                        CineX <span style={{ color: '#B21E2B' }}>Control</span>
                        <br />Center
                    </h1>

                    <p style={{
                        fontSize: '14px',
                        color: '#D8A63A',
                        fontWeight: '600',
                        marginBottom: '24px',
                        letterSpacing: '3px',
                        textTransform: 'uppercase',
                    }}>
                        Painel oficial de revendedores
                    </p>

                    <p style={{
                        fontSize: '15px',
                        color: '#A0A7B5',
                        lineHeight: '1.8',
                        marginBottom: '36px',
                        maxWidth: '380px',
                    }}>
                        Ative dispositivos CineX Player para seus clientes de forma rápida e automatizada.
                    </p>

                    {/* Feature pills */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '360px' }}>
                        {[
                            { icon: <Smartphone size={18} />, text: 'Gerencie dispositivos via MAC' },
                            { icon: <Zap size={18} />,        text: 'Envio de listas M3U e Xtream' },
                            { icon: <Shield size={18} />,     text: 'Sincronização com CineX Player' },
                        ].map((f, i) => (
                            <div key={i} style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '14px',
                                padding: '10px 18px',
                                background: 'rgba(255,255,255,0.03)',
                                borderRadius: '10px',
                                border: '1px solid rgba(216,166,58,0.08)',
                                backdropFilter: 'blur(8px)',
                            }}>
                                <div style={{ color: '#D8A63A', flexShrink: 0 }}>{f.icon}</div>
                                <span style={{ color: '#A0A7B5', fontSize: '13px' }}>{f.text}</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* ═══ RIGHT SIDE — LOGIN CARD (40%) ═══ */}
            <div style={{
                flex: '0 0 40%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '40px',
                position: 'relative',
                background: 'linear-gradient(180deg, rgba(11,15,26,1) 0%, rgba(18,18,28,1) 50%, rgba(11,15,26,1) 100%)',
            }}>
                {/* Subtle glow */}
                <div style={{
                    position: 'absolute',
                    top: '30%',
                    left: '40%',
                    width: '400px',
                    height: '400px',
                    background: 'radial-gradient(circle, rgba(178,30,43,0.08) 0%, transparent 70%)',
                    pointerEvents: 'none',
                }} />

                <div style={{
                    width: '100%',
                    maxWidth: '400px',
                    position: 'relative',
                    zIndex: 1,
                }}>
                    {/* Login Card */}
                    <div style={{
                        background: 'linear-gradient(145deg, rgba(26,26,26,0.95), rgba(13,13,20,0.98))',
                        borderRadius: '16px',
                        border: '1px solid rgba(216,166,58,0.1)',
                        padding: '44px 36px',
                        boxShadow: '0 24px 80px rgba(0,0,0,0.6), 0 0 1px rgba(216,166,58,0.2)',
                        backdropFilter: 'blur(20px)',
                    }}>
                        {/* Card Header */}
                        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                            <div style={{
                                width: '56px', height: '56px',
                                margin: '0 auto 18px',
                                background: 'linear-gradient(135deg, #B21E2B, #8E0F1E)',
                                borderRadius: '14px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                boxShadow: '0 4px 24px rgba(178,30,43,0.4)',
                            }}>
                                {/* eslint-disable-next-line @next/next/no-img-element */}
                                <img src="/logo_cinex.png" alt="CineX" width={40} height={40} style={{ objectFit: 'contain' }} />
                            </div>
                            <h2 style={{
                                fontSize: '22px',
                                fontWeight: '700',
                                color: '#FFFFFF',
                                marginBottom: '6px',
                            }}>
                                Acesso ao Painel
                            </h2>
                            <p style={{ fontSize: '13px', color: '#6E768A' }}>
                                Insira suas credenciais de parceiro
                            </p>
                        </div>

                        {/* Form */}
                        <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                            {error && (
                                <div style={{
                                    background: 'rgba(178, 30, 43, 0.1)',
                                    border: '1px solid rgba(178, 30, 43, 0.25)',
                                    color: '#E23A3A',
                                    padding: '12px 16px',
                                    borderRadius: '10px',
                                    fontSize: '13px',
                                    textAlign: 'center',
                                }}>
                                    {error}
                                </div>
                            )}

                            {/* Username */}
                            <div style={{ position: 'relative' }}>
                                <User size={18} style={{
                                    position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)',
                                    color: '#6E768A', pointerEvents: 'none',
                                }} />
                                <input
                                    type="text"
                                    placeholder="Usuário do Painel"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    required
                                    className="login-input"
                                    style={{
                                        width: '100%',
                                        padding: '14px 16px 14px 48px',
                                        background: 'rgba(11,15,26,0.8)',
                                        border: '1px solid rgba(255,255,255,0.06)',
                                        borderRadius: '10px',
                                        color: '#FFFFFF',
                                        fontSize: '14px',
                                        outline: 'none',
                                        transition: 'all 0.3s ease',
                                        boxSizing: 'border-box',
                                    }}
                                />
                            </div>

                            {/* Password */}
                            <div style={{ position: 'relative' }}>
                                <Lock size={18} style={{
                                    position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)',
                                    color: '#6E768A', pointerEvents: 'none',
                                }} />
                                <input
                                    type="password"
                                    placeholder="Senha do Painel"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    required
                                    className="login-input"
                                    style={{
                                        width: '100%',
                                        padding: '14px 16px 14px 48px',
                                        background: 'rgba(11,15,26,0.8)',
                                        border: '1px solid rgba(255,255,255,0.06)',
                                        borderRadius: '10px',
                                        color: '#FFFFFF',
                                        fontSize: '14px',
                                        outline: 'none',
                                        transition: 'all 0.3s ease',
                                        boxSizing: 'border-box',
                                    }}
                                />
                            </div>

                            {/* Submit Button */}
                            <button
                                type="submit"
                                disabled={loading}
                                className="btn-login"
                                style={{
                                    marginTop: '6px',
                                    padding: '14px',
                                    background: loading ? 'rgba(178,30,43,0.5)' : 'linear-gradient(135deg, #B21E2B, #E23A3A)',
                                    border: 'none',
                                    borderRadius: '10px',
                                    color: '#FFFFFF',
                                    fontSize: '15px',
                                    fontWeight: '600',
                                    cursor: loading ? 'not-allowed' : 'pointer',
                                    transition: 'all 0.3s ease',
                                    boxShadow: '0 4px 24px rgba(178,30,43,0.3)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '10px',
                                }}
                            >
                                {loading ? <Loader2 className="animate-spin" size={20} /> : 'Entrar no Painel'}
                            </button>
                        </form>

                        {/* Footer */}
                        <div style={{
                            textAlign: 'center',
                            marginTop: '28px',
                            paddingTop: '20px',
                            borderTop: '1px solid rgba(255,255,255,0.04)',
                        }}>
                            <p style={{ fontSize: '11px', color: '#6E768A', lineHeight: '1.7' }}>
                                © CineX Player
                            </p>
                            <p style={{ fontSize: '11px', color: '#4A5063', marginTop: '4px' }}>
                                Acesso restrito para revendedores autorizados.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}
