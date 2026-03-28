'use server'

import { supabase } from '@/lib/supabase'

function roundDown(n) {
    if (n < 10) return `${n}`
    if (n < 100) return `${Math.floor(n / 10) * 10}+`
    if (n < 1000) return `${Math.floor(n / 100) * 100}+`
    return `${Math.floor(n / 1000)}k+`
}

export async function getLoginStats() {
    try {
        // Downloads da última release do GitHub
        let downloads = 0
        try {
            const res = await fetch(
                'https://api.github.com/repos/BhendonWesley/CineX_Player/releases',
                { next: { revalidate: 3600 } }
            )
            if (res.ok) {
                const releases = await res.json()
                for (const release of releases) {
                    for (const asset of (release.assets || [])) {
                        if (asset.name.endsWith('.apk')) {
                            downloads += asset.download_count
                        }
                    }
                }
            }
        } catch (_) {}

        // Total de MACs cadastrados
        let totalMacs = 0
        try {
            const { count, error } = await supabase
                .from('devices')
                .select('*', { count: 'exact', head: true })
            if (!error && count != null) totalMacs = count
        } catch (_) {}

        return {
            downloads: roundDown(downloads),
            macs: roundDown(totalMacs),
        }
    } catch (_) {
        return { downloads: '0', macs: '0' }
    }
}
