'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, User, Loader2, Shield, Smartphone, Zap } from 'lucide-react'
import { loginAction } from './actions'

// ═══════════════════════════════════════════════════════════
//  70 unique TMDB posters — no duplicates
//  All verified from top-rated/popular movies
// ═══════════════════════════════════════════════════════════
const T = 'https://image.tmdb.org/t/p/w342'
const P = [
    // Row 1 — Action/Sci-Fi blockbusters
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
    // Row 2 — Drama/Thriller
    `${T}/sv1xJUazXeYqAyy0lB4w4Q3Rjya.jpg`,
    `${T}/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg`,
    `${T}/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg`,
    `${T}/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg`,
    `${T}/6CoRTJTmijhBLJTUNoVSUNxZMEI.jpg`,
    `${T}/dqK9Hag1054tghRQSqLSfrkvQnA.jpg`,
    `${T}/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg`,
    `${T}/uS1AIL7I1Ycgs8PTfqUeN6jYNsQ.jpg`,
    `${T}/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg`,
    `${T}/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg`,
    // Row 3 — Animated/Family
    `${T}/gPbM0MK8CP8A174kSwuaRnJKKrM.jpg`,
    `${T}/4m1Au3YkjqsxF8iwQy0fPYSxE0h.jpg`,
    `${T}/49WJfeN0moxb9IPfGn8AIqMGskD.jpg`,
    `${T}/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg`,
    `${T}/ym1dxyOk4jFcSvhGlWYee3Te1Kc.jpg`,
    `${T}/NNxYkU70HPurnNCSiCjYAmacwm.jpg`,
    // Row 4 — Classic/Award winners (well-known stable paths)
    `${T}/3bhkrj58Vtu7enYsRolD1fZdja1.jpg`,
    `${T}/rSPw7tgCH9c6NqICZef4kZjFOQ5.jpg`,
    `${T}/d9nBoowhjiiYc4FBNtQkN8VHoeM.jpg`,
    `${T}/3FUJT82YKY1EOwchMzMVR5MF1fV.jpg`,
    `${T}/hek3koDUyRQqa49MH2mOPV9jMSf.jpg`,
    `${T}/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg`,
    `${T}/aKuFiU82s5ISJpGZp7YkIR3UgPd.jpg`,
    `${T}/6oom5QYQ2yQTMJIbnvbkBL9cHo6.jpg`,
    `${T}/arw2vcBveWOVZr6pxd9XTd1TdQa.jpg`,
    `${T}/mXLOHHc1Zeuw0EAlsbRieWv3Ciy.jpg`,
    // Row 5 — Horror/Mystery
    `${T}/zGLHX92Gp44RITFWKB0tgKF0DO9.jpg`,
    `${T}/jWXrQstj7p3Wl5MnvGj3VIlsHBR.jpg`,
    `${T}/wuMc08IPKEatf9rnMNXvIDhIFlY.jpg`,
    `${T}/oAr5bgf49vxqa1UsBJF18SsmmWA.jpg`,
    `${T}/hB3DGHJz8aiCTmLuSHfYfG7RERU.jpg`,
    `${T}/7O4iVfOMQmdCSxhOg1WnzG1AgmM.jpg`,
    `${T}/b6qUu00iIIkXC8nkt57ganI9G0u.jpg`,
    `${T}/hS2mY2LJbuA2B3bPV8XzSGbkhsM.jpg`,
    // Row 6 — Recent hits
    `${T}/qhb1qOilapbapxWQn9jtRCMwXJF.jpg`,
    `${T}/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg`,
    `${T}/tlZpSxYuBRoVJBOpUrPdQe9FmFq.jpg`,
    `${T}/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg`,
    `${T}/vBZ0qvaRxqEhZBl0lnGbMakeIAp.jpg`,
    `${T}/vZloFAK7NmvMGKE7LPkHJ2HcPdH.jpg`,
    // Row 7 — Marvel/DC universe
    `${T}/pgqgaUx2LmVlQPYmlgrMxIigaaH.jpg`,
    `${T}/laMM4px0VGRDalTC3MhOOTpgMIl.jpg`,
    `${T}/ArGhYUcFnyJqhQ4vbWJCyNKESOb.jpg`,
    `${T}/3GrRgt6CiLIUXUxEqGOqAJoNSKj.jpg`,
    `${T}/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg`,
    `${T}/fiVW06jE7z9YnO4trhaMEdclSiA.jpg`,
    `${T}/v28T5F1IjMPLzpX4F12nJEIBbOk.jpg`,
    `${T}/62HCnUTziyWQpNt7uAnuBh7BbIU.jpg`,
    // Row 8 — More variety
    `${T}/kuf6dutpsT0vSVhYMFzOq3KVOQB.jpg`,
    `${T}/gxVcBRms23Mo4JEqGT4V4k4KFYA.jpg`,
    `${T}/sRLC052ieEzkQs9TsYOE4bvK1La.jpg`,
    `${T}/j8LGl0RoJB4IfTFA3Tl4MZAsYfh.jpg`,
    `${T}/pQ2mLuHJaGxYFhjnSBegwVsEhBs.jpg`,
    `${T}/oYuLEt3zVCKq57qu2Fgt9UTp898.jpg`,
    `${T}/vLinMiOMnalKz5emY8ItVsiVSMF.jpg`,
    `${T}/7IJ7F8vS1CiGMqMZQb3kCKLOf1r.jpg`,
    `${T}/8YFL5QQVPy3AgrEQxNYVSgiPEbe.jpg`,
    `${T}/74xTEgt7R36Fpooo50r9T25onGq.jpg`,
    `${T}/tkkmiiLbB8vXEiVbwTIWykiYBGE.jpg`,
    `${T}/eV3XzijD0XXeJzJPkdmdqAlhtWQ.jpg`,
]

// 14 columns, 5 unique posters each = 70 needed (exactly our list)
function buildColumns() {
    const cols = []
    const speeds = ['sa','sb','sc','sd','se','sf']
    for (let i = 0; i < 14; i++) {
        const colPosters = P.slice(i * 5, i * 5 + 5)
        cols.push({
            posters: colPosters,
            speed: speeds[i % 6],
            offset: (i % 3) * 30,
        })
    }
    return cols
}

const COLS = buildColumns()

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
        const fd = new FormData()
        fd.append('username', username)
        fd.append('password', password)
        try {
            const r = await loginAction(fd)
            if (r.success) router.push('/')
            else setError(r.message)
        } catch {
            setError('Falha na comunicação com o servidor.')
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            <style jsx global>{`
                .pw { position:absolute; inset:-40px; display:flex; gap:8px; overflow:hidden }
                .pc { flex:1; min-width:0; display:flex; flex-direction:column; gap:8px; will-change:transform }
                .pp { width:100%; aspect-ratio:2/3; border-radius:6px; object-fit:cover; flex-shrink:0; opacity:.5; display:block; background:rgba(255,255,255,.02) }
                .pp[data-e] { display:none }

                @keyframes su { 0%{transform:translateY(0)} 100%{transform:translateY(-50%)} }
                @keyframes sd { 0%{transform:translateY(-50%)} 100%{transform:translateY(0)} }
                .sa{animation:su 85s linear infinite} .sb{animation:sd 60s linear infinite}
                .sc{animation:su 45s linear infinite} .sd{animation:sd 80s linear infinite}
                .se{animation:su 50s linear infinite} .sf{animation:sd 38s linear infinite}
                @media(prefers-reduced-motion:reduce){.pc{animation:none!important}}

                .ov{position:absolute;inset:0;z-index:2;pointer-events:none;background:radial-gradient(ellipse at center,rgba(11,15,26,.55)0%,rgba(11,15,26,.9)70%)}
                .gw{position:absolute;top:10%;left:50%;transform:translateX(-50%);width:500px;height:500px;background:radial-gradient(circle,rgba(178,30,43,.12)0%,transparent 55%);pointer-events:none;z-index:3}

                .cc{background:rgba(11,15,26,.92);backdrop-filter:blur(28px)saturate(1.5);-webkit-backdrop-filter:blur(28px)saturate(1.5);border:1px solid rgba(216,166,58,.12);border-radius:20px;box-shadow:0 32px 80px rgba(0,0,0,.7),0 0 0 1px rgba(255,255,255,.04),inset 0 1px 0 rgba(255,255,255,.06);width:100%;max-width:420px;padding:36px 32px;position:relative;z-index:10}
                @media(max-width:480px){.cc{margin:16px;padding:28px 20px;max-width:calc(100vw - 32px);border-radius:16px}}

                .ci{width:100%;padding:13px 16px 13px 44px;background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.07);border-radius:10px;color:#fff;font-size:14px;outline:none;transition:border-color .3s,box-shadow .3s;box-sizing:border-box}
                .ci:focus{border-color:rgba(216,166,58,.45);box-shadow:0 0 20px rgba(216,166,58,.08)}
                .ci::placeholder{color:#5a6175}

                .cb{width:100%;padding:13px;background:linear-gradient(135deg,#B21E2B 0%,#E23A3A 100%);border:none;border-radius:10px;color:#fff;font-size:15px;font-weight:600;cursor:pointer;transition:all .3s;box-shadow:0 4px 24px rgba(178,30,43,.35);display:flex;align-items:center;justify-content:center;gap:10px}
                .cb:hover:not(:disabled){box-shadow:0 0 32px rgba(226,58,58,.5);transform:translateY(-1px)}
                .cb:disabled{opacity:.55;cursor:not-allowed}

                .cp{display:flex;align-items:center;gap:8px;padding:6px 12px;background:rgba(255,255,255,.03);border:1px solid rgba(216,166,58,.08);border-radius:8px;font-size:11px;color:#8a91a3}

                @keyframes spin{to{transform:rotate(360deg)}}
                .spin{animation:spin 1s linear infinite}
                .dv{height:1px;background:linear-gradient(90deg,transparent,rgba(216,166,58,.15),transparent);margin:16px 0}
            `}</style>

            <div style={{ position:'relative', minHeight:'100vh', width:'100%', background:'#0B0F1A', display:'flex', alignItems:'center', justifyContent:'center', overflow:'hidden' }}>
                {/* ═══ FULLSCREEN POSTER WALL — 14 unique columns ═══ */}
                <div className="pw">
                    {COLS.map((col, ci) => {
                        const doubled = [...col.posters, ...col.posters]
                        return (
                            <div key={ci} className={`pc ${col.speed}`} style={{ paddingTop: col.offset }}>
                                {doubled.map((src, i) => (
                                    <img key={`${ci}-${i}`} src={src} alt="" className="pp"
                                        decoding="async"
                                        onError={e => { e.target.setAttribute('data-e','1'); e.target.style.display='none' }}
                                    />
                                ))}
                            </div>
                        )
                    })}
                </div>

                <div className="ov" />
                <div className="gw" />

                {/* ═══ UNIFIED CARD ═══ */}
                <div className="cc">
                    <div style={{ textAlign:'center', marginBottom:'6px' }}>
                        <div style={{ width:'52px', height:'52px', margin:'0 auto 12px', background:'linear-gradient(135deg,#B21E2B,#8E0F1E)', borderRadius:'14px', display:'flex', alignItems:'center', justifyContent:'center', boxShadow:'0 4px 24px rgba(178,30,43,.4)' }}>
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src="/logo_cinex.png" alt="CineX" width={36} height={36} style={{ objectFit:'contain' }} onError={e => { e.target.style.display='none' }} />
                        </div>
                        <h1 style={{ fontSize:'24px', fontWeight:'800', color:'#fff', marginBottom:'2px', letterSpacing:'-0.5px' }}>
                            CineX <span style={{ color:'#B21E2B' }}>Control Center</span>
                        </h1>
                        <p style={{ fontSize:'10px', color:'#D8A63A', fontWeight:'600', letterSpacing:'2.5px', textTransform:'uppercase' }}>
                            Painel oficial de revendedores
                        </p>
                    </div>

                    <div className="dv" />

                    <div style={{ display:'flex', gap:'6px', marginBottom:'16px', flexWrap:'wrap', justifyContent:'center' }}>
                        <div className="cp"><Smartphone size={13} style={{ color:'#D8A63A', flexShrink:0 }} /><span>Dispositivos via MAC</span></div>
                        <div className="cp"><Zap size={13} style={{ color:'#D8A63A', flexShrink:0 }} /><span>M3U e Xtream</span></div>
                        <div className="cp"><Shield size={13} style={{ color:'#D8A63A', flexShrink:0 }} /><span>Sync CineX Player</span></div>
                    </div>

                    <div style={{ textAlign:'center', marginBottom:'16px' }}>
                        <p style={{ fontSize:'12px', color:'#fff', fontWeight:'500', background:'rgba(216,166,58,.1)', padding:'8px 12px', borderRadius:'8px', border:'1px solid rgba(216,166,58,.2)' }}>
                            Use o mesmo acesso do painel CineX para entrar no painel de controle
                        </p>
                    </div>

                    <form onSubmit={handleLogin} style={{ display:'flex', flexDirection:'column', gap:'12px' }}>
                        {error && (
                            <div style={{ background:'rgba(178,30,43,.1)', border:'1px solid rgba(178,30,43,.25)', color:'#E23A3A', padding:'10px 14px', borderRadius:'10px', fontSize:'13px', textAlign:'center' }}>{error}</div>
                        )}
                        <div style={{ position:'relative' }}>
                            <User size={16} style={{ position:'absolute', left:'14px', top:'50%', transform:'translateY(-50%)', color:'#5a6175', pointerEvents:'none' }} />
                            <input type="text" placeholder="Usuário do Painel" value={username} onChange={e=>setUsername(e.target.value)} required className="ci" />
                        </div>
                        <div style={{ position:'relative' }}>
                            <Lock size={16} style={{ position:'absolute', left:'14px', top:'50%', transform:'translateY(-50%)', color:'#5a6175', pointerEvents:'none' }} />
                            <input type="password" placeholder="Senha do Painel" value={password} onChange={e=>setPassword(e.target.value)} required className="ci" />
                        </div>
                        <button type="submit" disabled={loading} className="cb" style={{ marginTop:'2px' }}>
                            {loading ? <Loader2 className="spin" size={20} /> : 'Entrar no Painel'}
                        </button>
                    </form>

                    <div style={{ textAlign:'center', marginTop:'16px' }}>
                        <p style={{ fontSize:'10px', color:'#4a5063' }}>© CineX Player — Acesso restrito para revendedores autorizados.</p>
                    </div>
                </div>
            </div>
        </>
    )
}
