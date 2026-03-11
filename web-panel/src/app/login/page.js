'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, User, Loader2, Shield, Smartphone, Zap } from 'lucide-react'
import { loginAction } from './actions'

// ═══════════════════════════════════════════════════════════
//  TMDB verified poster paths — w342 for fast loading
// ═══════════════════════════════════════════════════════════
const T = 'https://image.tmdb.org/t/p/w342'
const ALL_POSTERS = [
    `${T}/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg`,
    `${T}/qJ2tW6WMUDux911SbMqGMYV4f3R.jpg`,
    `${T}/d5NXSklXo0qyIYkgV94XAgMIckC.jpg`,
    `${T}/z1p34vh7dEOnLDV4jC1DGcKSqRm.jpg`,
    `${T}/ngl2FKBlU4fhbdsrtdom9LVLBXw.jpg`,
    `${T}/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg`,
    `${T}/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg`,
    `${T}/7WsyChQLEftFiDhRDpZFHSNbCYc.jpg`,
    `${T}/pB8BM7pdSp6B6Ih7QI4S2t0POoT.jpg`,
    `${T}/ldfCF9RhR40l1UkalGw99cHVmhY.jpg`,
    `${T}/sv1xJUazXeYqAyy0lB4w4Q3Rjya.jpg`,
    `${T}/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg`,
    `${T}/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg`,
    `${T}/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg`,
    `${T}/6CoRTJTmijhBLJTUNoVSUNxZMEI.jpg`,
    `${T}/pIkGQo1lhmGn8MIKHMRhJoHT0y2.jpg`,
    `${T}/dqK9Hag1054tghRQSqLSfrkvQnA.jpg`,
    `${T}/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg`,
    `${T}/uS1AIL7I1Ycgs8PTfqUeN6jYNsQ.jpg`,
    `${T}/AkNiKuRLPfj1XXwXNlXjNXPq6n3.jpg`,
    `${T}/sF1U4EUQS8YMrncNmIk3LaZIbZk.jpg`,
    `${T}/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg`,
    `${T}/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg`,
    `${T}/gPbM0MK8CP8A174kSwuaRnJKKrM.jpg`,
    `${T}/49WJfeN0moxb9IPfGn8AIqMGskD.jpg`,
    `${T}/vpnVM9B6NMmQpWeZvzLvDESb2gY.jpg`,
    `${T}/4m1Au3YkjqsxF8iwQy0fPYSxE0h.jpg`,
    `${T}/1E5baAaEse26fej7uHcjOgEERB2.jpg`,
    `${T}/hu40Uj7mEQR2LgRhroQMlXfOdjr.jpg`,
    `${T}/5gzzkR7y3qqY8BLYnSA7o0vqqzU.jpg`,
    `${T}/8b8R8l88Qje9dn9OE8PY05Nez7H.jpg`,
    `${T}/ym1dxyOk4jFcSvhGlWYee3Te1Kc.jpg`,
    `${T}/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg`,
    `${T}/pnXLFioDeftqjlCVlRmXvIdMsdh.jpg`,
]

// Generate columns dynamically — fill entire viewport width
function buildColumns(count) {
    const cols = []
    const speeds = ['up-slow', 'down-med', 'up-fast', 'down-slow', 'up-med', 'down-fast']
    for (let i = 0; i < count; i++) {
        const start = (i * 4) % ALL_POSTERS.length
        const posters = []
        for (let j = 0; j < 6; j++) {
            posters.push(ALL_POSTERS[(start + j) % ALL_POSTERS.length])
        }
        cols.push({ posters, speed: speeds[i % speeds.length], offset: i % 2 === 0 ? 0 : 30 })
    }
    return cols
}

const COLUMNS = buildColumns(14) // 14 columns fills even ultrawide

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
            if (result.success) router.push('/')
            else setError(result.message)
        } catch {
            setError('Falha na comunicação com o servidor.')
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            <style jsx global>{`
                .pw-grid {
                    display: flex;
                    gap: 8px;
                    position: absolute;
                    inset: -20px;
                    justify-content: center;
                    overflow: hidden;
                }
                .pw-col {
                    display: flex;
                    flex-direction: column;
                    gap: 8px;
                    flex-shrink: 0;
                    width: calc((100vw + 40px) / 14);
                    min-width: 80px;
                    will-change: transform;
                }
                .pw-poster {
                    width: 100%;
                    aspect-ratio: 2/3;
                    border-radius: 8px;
                    object-fit: cover;
                    flex-shrink: 0;
                    opacity: 0.55;
                    background: rgba(255,255,255,0.02);
                }

                @keyframes scrollUp   { 0%{transform:translateY(0)}    100%{transform:translateY(-50%)} }
                @keyframes scrollDown  { 0%{transform:translateY(-50%)} 100%{transform:translateY(0)} }
                .up-slow   { animation: scrollUp   90s linear infinite; }
                .up-med    { animation: scrollUp   60s linear infinite; }
                .up-fast   { animation: scrollUp   42s linear infinite; }
                .down-slow { animation: scrollDown 85s linear infinite; }
                .down-med  { animation: scrollDown 55s linear infinite; }
                .down-fast { animation: scrollDown 40s linear infinite; }

                @media (prefers-reduced-motion: reduce) {
                    .pw-col { animation: none !important; }
                }

                /* Mobile: fewer visible columns, smaller gaps */
                @media (max-width: 640px) {
                    .pw-col { width: calc(100vw / 6); min-width: 55px; }
                    .pw-grid { gap: 5px; }
                    .pw-poster { border-radius: 5px; }
                }
                @media (min-width: 641px) and (max-width: 1024px) {
                    .pw-col { width: calc(100vw / 10); }
                }

                /* Card */
                .cx-card {
                    background: rgba(11,15,26,0.9);
                    backdrop-filter: blur(28px) saturate(1.5);
                    -webkit-backdrop-filter: blur(28px) saturate(1.5);
                    border: 1px solid rgba(216,166,58,0.12);
                    border-radius: 20px;
                    box-shadow:
                        0 32px 80px rgba(0,0,0,0.7),
                        0 0 0 1px rgba(255,255,255,0.04),
                        inset 0 1px 0 rgba(255,255,255,0.06);
                    width: 100%;
                    max-width: 420px;
                    padding: 36px 32px;
                    position: relative;
                    z-index: 10;
                }
                @media (max-width: 480px) {
                    .cx-card {
                        margin: 16px;
                        padding: 28px 20px;
                        max-width: calc(100vw - 32px);
                        border-radius: 16px;
                    }
                }

                .cx-input {
                    width: 100%;
                    padding: 13px 16px 13px 44px;
                    background: rgba(255,255,255,0.04);
                    border: 1px solid rgba(255,255,255,0.07);
                    border-radius: 10px;
                    color: #fff;
                    font-size: 14px;
                    outline: none;
                    transition: border-color .3s, box-shadow .3s;
                    box-sizing: border-box;
                }
                .cx-input:focus {
                    border-color: rgba(216,166,58,0.45);
                    box-shadow: 0 0 20px rgba(216,166,58,0.08);
                }
                .cx-input::placeholder { color: #5a6175; }

                .cx-btn {
                    width: 100%;
                    padding: 13px;
                    background: linear-gradient(135deg, #B21E2B 0%, #E23A3A 100%);
                    border: none;
                    border-radius: 10px;
                    color: #fff;
                    font-size: 15px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all .3s;
                    box-shadow: 0 4px 24px rgba(178,30,43,0.35);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 10px;
                }
                .cx-btn:hover:not(:disabled) {
                    box-shadow: 0 0 32px rgba(226,58,58,0.5), 0 6px 28px rgba(178,30,43,0.4);
                    transform: translateY(-1px);
                }
                .cx-btn:disabled { opacity: 0.55; cursor: not-allowed; }

                .feat-pill {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    padding: 6px 12px;
                    background: rgba(255,255,255,0.03);
                    border: 1px solid rgba(216,166,58,0.08);
                    border-radius: 8px;
                    font-size: 11px;
                    color: #8a91a3;
                }

                @keyframes spin { to { transform: rotate(360deg); } }
                .spin { animation: spin 1s linear infinite; }

                .cx-divider {
                    height: 1px;
                    background: linear-gradient(90deg, transparent, rgba(216,166,58,0.15), transparent);
                    margin: 16px 0;
                }
            `}</style>

            <div style={{
                position: 'relative',
                minHeight: '100vh',
                width: '100%',
                background: '#0B0F1A',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                overflow: 'hidden',
            }}>
                {/* ═══ FULLSCREEN POSTER WALL — 14 columns ═══ */}
                <div className="pw-grid">
                    {COLUMNS.map((col, ci) => {
                        const doubled = [...col.posters, ...col.posters]
                        return (
                            <div
                                key={ci}
                                className={`pw-col ${col.speed}`}
                                style={{ paddingTop: col.offset }}
                            >
                                {doubled.map((src, i) => (
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img key={i} src={src} alt="" className="pw-poster" loading="lazy" decoding="async" />
                                ))}
                            </div>
                        )
                    })}
                </div>

                {/* ═══ DARK OVERLAY ═══ */}
                <div style={{
                    position: 'absolute', inset: 0, zIndex: 2,
                    background: 'radial-gradient(ellipse at center, rgba(11,15,26,0.6) 0%, rgba(11,15,26,0.88) 65%)',
                    pointerEvents: 'none',
                }} />
                {/* Red ambient glow */}
                <div style={{
                    position: 'absolute', top: '15%', left: '50%', transform: 'translateX(-50%)',
                    width: '500px', height: '500px',
                    background: 'radial-gradient(circle, rgba(178,30,43,0.12) 0%, transparent 60%)',
                    pointerEvents: 'none', zIndex: 3,
                }} />

                {/* ═══ UNIFIED CARD ═══ */}
                <div className="cx-card">
                    {/* Logo */}
                    <div style={{ textAlign: 'center', marginBottom: '6px' }}>
                        <div style={{
                            width: '52px', height: '52px',
                            margin: '0 auto 12px',
                            background: 'linear-gradient(135deg, #B21E2B, #8E0F1E)',
                            borderRadius: '14px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 4px 24px rgba(178,30,43,0.4)',
                        }}>
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src="/logo_cinex.png" alt="CineX" width={36} height={36} style={{ objectFit: 'contain' }} />
                        </div>
                        <h1 style={{ fontSize: '24px', fontWeight: '800', color: '#fff', marginBottom: '2px', letterSpacing: '-0.5px' }}>
                            CineX <span style={{ color: '#B21E2B' }}>Control Center</span>
                        </h1>
                        <p style={{ fontSize: '10px', color: '#D8A63A', fontWeight: '600', letterSpacing: '2.5px', textTransform: 'uppercase' }}>
                            Painel oficial de revendedores
                        </p>
                    </div>

                    <div className="cx-divider" />

                    {/* Features */}
                    <div style={{ display: 'flex', gap: '6px', marginBottom: '16px', flexWrap: 'wrap', justifyContent: 'center' }}>
                        <div className="feat-pill">
                            <Smartphone size={13} style={{ color: '#D8A63A', flexShrink: 0 }} />
                            <span>Dispositivos via MAC</span>
                        </div>
                        <div className="feat-pill">
                            <Zap size={13} style={{ color: '#D8A63A', flexShrink: 0 }} />
                            <span>M3U e Xtream</span>
                        </div>
                        <div className="feat-pill">
                            <Shield size={13} style={{ color: '#D8A63A', flexShrink: 0 }} />
                            <span>Sync CineX Player</span>
                        </div>
                    </div>

                    {/* Form */}
                    <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {error && (
                            <div style={{
                                background: 'rgba(178,30,43,0.1)',
                                border: '1px solid rgba(178,30,43,0.25)',
                                color: '#E23A3A',
                                padding: '10px 14px',
                                borderRadius: '10px',
                                fontSize: '13px',
                                textAlign: 'center',
                            }}>
                                {error}
                            </div>
                        )}
                        <div style={{ position: 'relative' }}>
                            <User size={16} style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: '#5a6175', pointerEvents: 'none' }} />
                            <input type="text" placeholder="Usuário do Painel" value={username} onChange={(e) => setUsername(e.target.value)} required className="cx-input" />
                        </div>
                        <div style={{ position: 'relative' }}>
                            <Lock size={16} style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: '#5a6175', pointerEvents: 'none' }} />
                            <input type="password" placeholder="Senha do Painel" value={password} onChange={(e) => setPassword(e.target.value)} required className="cx-input" />
                        </div>
                        <button type="submit" disabled={loading} className="cx-btn" style={{ marginTop: '2px' }}>
                            {loading ? <Loader2 className="spin" size={20} /> : 'Entrar no Painel'}
                        </button>
                    </form>

                    {/* Footer */}
                    <div style={{ textAlign: 'center', marginTop: '16px' }}>
                        <p style={{ fontSize: '10px', color: '#4a5063' }}>
                            © CineX Player — Acesso restrito para revendedores autorizados.
                        </p>
                    </div>
                </div>
            </div>
        </>
    )
}
