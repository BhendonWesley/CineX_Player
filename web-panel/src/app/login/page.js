'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, User, Loader2, Shield, Smartphone, Zap } from 'lucide-react'
import { loginAction } from './actions'

// ═══════════════════════════════════════════════════════════
//  Verified TMDB poster paths (popular, high-quality covers)
// ═══════════════════════════════════════════════════════════
const P = 'https://image.tmdb.org/t/p/w342'
const COLS = [
    // Col 0 — large, slow
    [ `${P}/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg`,  // Oppenheimer
      `${P}/qJ2tW6WMUDux911SbMqGMYV4f3R.jpg`,  // Interstellar
      `${P}/d5NXSklXo0qyIYkgV94XAgMIckC.jpg`,  // Dune 2
      `${P}/z1p34vh7dEOnLDV4jC1DGcKSqRm.jpg`,  // Avengers Endgame
      `${P}/ngl2FKBlU4fhbdsrtdom9LVLBXw.jpg`,  // Wonka
    ],
    // Col 1 — medium, medium
    [ `${P}/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg`,  // Deadpool
      `${P}/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg`,  // Gladiator 2
      `${P}/7WsyChQLEftFiDhRDpZFHSNbCYc.jpg`,  // Batman
      `${P}/pB8BM7pdSp6B6Ih7QI4S2t0POoT.jpg`,  // Inside Out 2
      `${P}/ldfCF9RhR40l1UkalGw99cHVmhY.jpg`,  // Inception
      `${P}/sv1xJUazXeYqAyy0lB4w4Q3Rjya.jpg`,  // Joker 2
    ],
    // Col 2 — small, fast
    [ `${P}/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg`,  // Kung Fu Panda 4
      `${P}/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg`,  // Despicable Me 4
      `${P}/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg`,  // Godzilla vs Kong
      `${P}/6CoRTJTmijhBLJTUNoVSUNxZMEI.jpg`,  // Shawshank
      `${P}/pIkGQo1lhmGn8MIKHMRhJoHT0y2.jpg`,  // Turning Red
      `${P}/dqK9Hag1054tghRQSqLSfrkvQnA.jpg`,  // Furiosa
      `${P}/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg`,  // Moana 2
    ],
    // Col 3 — large, medium-reverse
    [ `${P}/uS1AIL7I1Ycgs8PTfqUeN6jYNsQ.jpg`,  // Spider-Man NWH
      `${P}/AkNiKuRLPfj1XXwXNlXjNXPq6n3.jpg`,  // Frozen 2
      `${P}/sF1U4EUQS8YMrncNmIk3LaZIbZk.jpg`,  // Top Gun Maverick
      `${P}/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg`,  // Barbie
      `${P}/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg`,  // John Wick 4
    ],
    // Col 4 — medium, slow-reverse
    [ `${P}/gPbM0MK8CP8A174kSwuaRnJKKrM.jpg`,  // Avatar 2
      `${P}/49WJfeN0moxb9IPfGn8AIqMGskD.jpg`,  // Aquaman 2
      `${P}/vpnVM9B6NMmQpWeZvzLvDESb2gY.jpg`,  // Elemental
      `${P}/4m1Au3YkjqsxF8iwQy0fPYSxE0h.jpg`,  // Mario Movie
      `${P}/1E5baAaEse26fej7uHcjOgEERB2.jpg`,  // Napoleon
      `${P}/hu40Uj7mEQR2LgRhroQMlXfOdjr.jpg`,  // Wish
    ],
    // Col 5 — small, fast-reverse
    [ `${P}/NNxYkU70HPurnNCSiCjYAmacwm.jpg`,   // Migration
      `${P}/5gzzkR7y3qqY8BLYnSA7o0vqqzU.jpg`,  // Wicked
      `${P}/8b8R8l88Qje9dn9OE8PY05Nez7H.jpg`,  // Venom 3
      `${P}/ym1dxyOk4jFcSvhGlWYee3Te1Kc.jpg`,  // The Creator
      `${P}/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg`,  // Dune 1
      `${P}/pnXLFioDeftqjlCVlRmXvIdMsdh.jpg`,  // Knives Out
      `${P}/lVcORnnBCOTfRr02g7h4vR1UFHJ.jpg`,  // Smile 2
    ],
]

const COL_CFG = [
    { cls: 'wall-lg',  anim: 'wall-slow' },
    { cls: 'wall-md',  anim: 'wall-med' },
    { cls: 'wall-sm',  anim: 'wall-fast' },
    { cls: 'wall-lg',  anim: 'wall-med-r' },
    { cls: 'wall-md',  anim: 'wall-slow-r' },
    { cls: 'wall-sm',  anim: 'wall-fast-r' },
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
            {/* Inline styles for wall animations — avoids globals.css bloat */}
            <style jsx global>{`
                /* ═══ POSTER WALL SYSTEM ═══ */
                .wall-grid {
                    display: flex;
                    gap: 10px;
                    position: absolute;
                    inset: 0;
                    justify-content: center;
                    overflow: hidden;
                }
                .wall-col {
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                    will-change: transform;
                }
                .wall-poster {
                    border-radius: 8px;
                    object-fit: cover;
                    flex-shrink: 0;
                    background: rgba(255,255,255,0.03);
                }
                /* Sizes */
                .wall-lg { width: 180px; }
                .wall-lg .wall-poster { width: 180px; height: 270px; opacity: 0.7; }
                .wall-md { width: 140px; }
                .wall-md .wall-poster { width: 140px; height: 210px; opacity: 0.5; }
                .wall-sm { width: 110px; }
                .wall-sm .wall-poster { width: 110px; height: 165px; opacity: 0.35; filter: blur(1.5px); }

                /* Scroll animations — duplicated content scrolls seamlessly */
                @keyframes wallUp   { 0%{transform:translateY(0)}    100%{transform:translateY(-50%)} }
                @keyframes wallDown { 0%{transform:translateY(-50%)} 100%{transform:translateY(0)} }
                .wall-slow   { animation: wallUp   80s linear infinite; }
                .wall-med    { animation: wallDown 55s linear infinite; }
                .wall-fast   { animation: wallUp   40s linear infinite; }
                .wall-slow-r { animation: wallDown 75s linear infinite; }
                .wall-med-r  { animation: wallUp   50s linear infinite; }
                .wall-fast-r { animation: wallDown 38s linear infinite; }

                /* Pause on low-power / prefers-reduced-motion */
                @media (prefers-reduced-motion: reduce) {
                    .wall-col { animation: none !important; }
                }

                /* ═══ RESPONSIVE ═══ */
                @media (max-width: 640px) {
                    .wall-lg { width: 90px; }
                    .wall-lg .wall-poster { width: 90px; height: 135px; }
                    .wall-md { width: 70px; }
                    .wall-md .wall-poster { width: 70px; height: 105px; }
                    .wall-sm { width: 55px; }
                    .wall-sm .wall-poster { width: 55px; height: 82px; }
                    .wall-grid { gap: 6px; }
                    .wall-col { gap: 6px; }
                }
                @media (min-width: 641px) and (max-width: 1024px) {
                    .wall-lg { width: 130px; }
                    .wall-lg .wall-poster { width: 130px; height: 195px; }
                    .wall-md { width: 100px; }
                    .wall-md .wall-poster { width: 100px; height: 150px; }
                    .wall-sm { width: 80px; }
                    .wall-sm .wall-poster { width: 80px; height: 120px; }
                }

                /* ═══ CARD GLASS ═══ */
                .login-card {
                    background: rgba(11,15,26,0.88);
                    backdrop-filter: blur(24px) saturate(1.4);
                    -webkit-backdrop-filter: blur(24px) saturate(1.4);
                    border: 1px solid rgba(216,166,58,0.12);
                    border-radius: 20px;
                    box-shadow:
                        0 32px 80px rgba(0,0,0,0.6),
                        0 0 0 1px rgba(255,255,255,0.04),
                        inset 0 1px 0 rgba(255,255,255,0.05);
                    width: 100%;
                    max-width: 440px;
                    padding: 40px 36px;
                    position: relative;
                    z-index: 10;
                }
                @media (max-width: 480px) {
                    .login-card {
                        margin: 16px;
                        padding: 28px 22px;
                        max-width: calc(100vw - 32px);
                        border-radius: 16px;
                    }
                }

                /* ═══ INPUT FOCUS ═══ */
                .cx-input {
                    width: 100%;
                    padding: 14px 16px 14px 46px;
                    background: rgba(255,255,255,0.04);
                    border: 1px solid rgba(255,255,255,0.07);
                    border-radius: 12px;
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

                /* ═══ BUTTON ═══ */
                .cx-btn {
                    width: 100%;
                    padding: 14px;
                    background: linear-gradient(135deg, #B21E2B 0%, #E23A3A 100%);
                    border: none;
                    border-radius: 12px;
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
                .cx-btn:disabled {
                    opacity: 0.55;
                    cursor: not-allowed;
                }

                /* ═══ FEATURE PILL ═══ */
                .feat-pill {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    padding: 8px 14px;
                    background: rgba(255,255,255,0.03);
                    border: 1px solid rgba(216,166,58,0.07);
                    border-radius: 8px;
                    font-size: 12px;
                    color: #8a91a3;
                    transition: border-color .3s;
                }
                .feat-pill:hover {
                    border-color: rgba(216,166,58,0.2);
                }

                /* ═══ SPIN ═══ */
                @keyframes spin { to { transform: rotate(360deg); } }
                .spin { animation: spin 1s linear infinite; }

                /* ═══ DIVIDER ═══ */
                .cx-divider {
                    height: 1px;
                    background: linear-gradient(90deg, transparent, rgba(216,166,58,0.15), transparent);
                    margin: 20px 0;
                }
            `}</style>

            {/* ═══════════ PAGE CONTAINER ═══════════ */}
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

                {/* ═══ FULLSCREEN POSTER WALL ═══ */}
                <div className="wall-grid">
                    {COLS.map((col, ci) => {
                        const cfg = COL_CFG[ci]
                        const doubled = [...col, ...col] // seamless loop
                        return (
                            <div
                                key={ci}
                                className={`wall-col ${cfg.cls} ${cfg.anim}`}
                                style={{ paddingTop: ci % 2 === 0 ? 0 : 40 }}
                            >
                                {doubled.map((src, i) => (
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img
                                        key={i}
                                        src={src}
                                        alt=""
                                        className="wall-poster"
                                        loading="lazy"
                                        decoding="async"
                                    />
                                ))}
                            </div>
                        )
                    })}
                </div>

                {/* ═══ DARK OVERLAY ═══ */}
                <div style={{
                    position: 'absolute', inset: 0, zIndex: 2,
                    background: `
                        radial-gradient(ellipse at center, rgba(11,15,26,0.65) 0%, rgba(11,15,26,0.92) 70%),
                        linear-gradient(180deg, rgba(11,15,26,0.5) 0%, rgba(11,15,26,0.3) 40%, rgba(11,15,26,0.7) 100%)
                    `,
                    pointerEvents: 'none',
                }} />

                {/* ═══ RED AMBIENT GLOW ═══ */}
                <div style={{
                    position: 'absolute', top: '20%', left: '50%',
                    transform: 'translateX(-50%)',
                    width: '600px', height: '600px',
                    background: 'radial-gradient(circle, rgba(178,30,43,0.1) 0%, transparent 60%)',
                    pointerEvents: 'none', zIndex: 3,
                }} />

                {/* ═══════════ UNIFIED LOGIN CARD ═══════════ */}
                <div className="login-card" style={{ zIndex: 10 }}>

                    {/* ── LOGO + BRAND ── */}
                    <div style={{ textAlign: 'center', marginBottom: '8px' }}>
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                            src="/logo_cinex.png"
                            alt="CineX"
                            width={56} height={56}
                            style={{
                                objectFit: 'contain',
                                filter: 'drop-shadow(0 0 20px rgba(178,30,43,0.5))',
                                marginBottom: '14px',
                            }}
                        />
                        <h1 style={{
                            fontSize: '26px', fontWeight: '800',
                            color: '#fff', marginBottom: '2px', letterSpacing: '-0.5px',
                        }}>
                            CineX <span style={{ color: '#B21E2B' }}>Control Center</span>
                        </h1>
                        <p style={{
                            fontSize: '11px', color: '#D8A63A', fontWeight: '600',
                            letterSpacing: '2.5px', textTransform: 'uppercase', marginBottom: '4px',
                        }}>
                            Painel oficial de revendedores
                        </p>
                    </div>

                    <div className="cx-divider" />

                    {/* ── FEATURE PILLS ── */}
                    <div style={{ display: 'flex', gap: '8px', marginBottom: '20px', flexWrap: 'wrap', justifyContent: 'center' }}>
                        <div className="feat-pill">
                            <Smartphone size={14} style={{ color: '#D8A63A', flexShrink: 0 }} />
                            <span>Dispositivos via MAC</span>
                        </div>
                        <div className="feat-pill">
                            <Zap size={14} style={{ color: '#D8A63A', flexShrink: 0 }} />
                            <span>M3U e Xtream</span>
                        </div>
                        <div className="feat-pill">
                            <Shield size={14} style={{ color: '#D8A63A', flexShrink: 0 }} />
                            <span>Sync CineX Player</span>
                        </div>
                    </div>

                    {/* ── FORM ── */}
                    <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
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
                            <User size={17} style={{
                                position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)',
                                color: '#5a6175', pointerEvents: 'none',
                            }} />
                            <input
                                type="text"
                                placeholder="Usuário do Painel"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                required
                                className="cx-input"
                            />
                        </div>

                        <div style={{ position: 'relative' }}>
                            <Lock size={17} style={{
                                position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)',
                                color: '#5a6175', pointerEvents: 'none',
                            }} />
                            <input
                                type="password"
                                placeholder="Senha do Painel"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                                className="cx-input"
                            />
                        </div>

                        <button type="submit" disabled={loading} className="cx-btn" style={{ marginTop: '4px' }}>
                            {loading ? <Loader2 className="spin" size={20} /> : 'Entrar no Painel'}
                        </button>
                    </form>

                    {/* ── FOOTER ── */}
                    <div style={{ textAlign: 'center', marginTop: '20px' }}>
                        <p style={{ fontSize: '11px', color: '#4a5063' }}>
                            © CineX Player — Acesso restrito para revendedores autorizados.
                        </p>
                    </div>
                </div>
            </div>
        </>
    )
}
