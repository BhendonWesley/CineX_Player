import { NextResponse } from 'next/server'
import { supabase } from '@/lib/supabase'

// API pública que o app Android consulta para buscar a playlist pelo MAC
export async function GET(request, { params }) {
    const { mac } = await params

    if (!mac) {
        return NextResponse.json({ error: 'MAC address required' }, { status: 400 })
    }

    const { data: device, error } = await supabase
        .from('devices')
        .select('*')
        .eq('mac_address', mac.toUpperCase())
        .single()

    if (error || !device) {
        return NextResponse.json({
            mac: mac,
            sync_status: 'not_found',
            message: 'Dispositivo não cadastrado. Contate seu revendedor.'
        }, { status: 404 })
    }

    return NextResponse.json({
        mac: device.mac_address,
        sync_status: 'success',
        playlist: {
            type: device.playlist_type,
            ...(device.playlist_config || {})
        }
    })
}
