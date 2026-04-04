import { NextResponse } from 'next/server'
import { supabase } from '@/lib/supabase'

/**
 * GET /api/tmdb-cache?keys=movie:barbie 2023,tv:breaking bad
 * 
 * Busca múltiplas entradas do cache TMDB de uma vez (batch).
 * Retorna um mapa { cache_key: { poster_url, banner_url, ... } }
 */
export async function GET(request) {
    const { searchParams } = new URL(request.url)
    const keysParam = searchParams.get('keys')

    if (!keysParam) {
        return NextResponse.json({ error: 'keys parameter required' }, { status: 400 })
    }

    const keys = keysParam.split(',').map(k => k.trim()).filter(Boolean).slice(0, 100)
    if (keys.length === 0) {
        return NextResponse.json({ data: {} })
    }

    const { data, error } = await supabase
        .from('tmdb_cache')
        .select('*')
        .in('cache_key', keys)

    if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 })
    }

    const result = {}
    for (const row of (data || [])) {
        result[row.cache_key] = {
            poster_url: row.poster_url,
            banner_url: row.banner_url,
            synopsis: row.synopsis,
            rating: row.rating,
            year: row.year,
            trailer_url: row.trailer_url,
            cast_members: row.cast_members,
            updated_at: row.updated_at
        }
    }

    return NextResponse.json({ data: result })
}

/**
 * POST /api/tmdb-cache
 * Body: { entries: [{ cache_key, poster_url, banner_url, synopsis, rating, year, trailer_url, cast_members }] }
 * 
 * Salva múltiplas entradas no cache TMDB (upsert batch).
 */
export async function POST(request) {
    try {
        const body = await request.json()
        const entries = body.entries

        if (!entries || !Array.isArray(entries) || entries.length === 0) {
            return NextResponse.json({ error: 'entries array required' }, { status: 400 })
        }

        const rows = entries.slice(0, 50).map(e => ({
            cache_key: e.cache_key,
            poster_url: e.poster_url || null,
            banner_url: e.banner_url || null,
            synopsis: e.synopsis || null,
            rating: e.rating || null,
            year: e.year || null,
            trailer_url: e.trailer_url || null,
            cast_members: e.cast_members || null,
            updated_at: new Date().toISOString()
        })).filter(r => r.cache_key)

        const { error } = await supabase
            .from('tmdb_cache')
            .upsert(rows, { onConflict: 'cache_key' })

        if (error) {
            return NextResponse.json({ error: error.message }, { status: 500 })
        }

        return NextResponse.json({ success: true, saved: rows.length })
    } catch (e) {
        return NextResponse.json({ error: e.message }, { status: 500 })
    }
}
