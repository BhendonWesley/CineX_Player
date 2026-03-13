import { NextResponse } from 'next/server'
import { supabase } from '@/lib/supabase'

// API pública que o app Android consulta para buscar a playlist pelo MAC
export async function GET(request, { params }) {
    const { mac } = await params

    if (!mac) {
        return NextResponse.json({ error: 'MAC address required' }, { status: 400 })
    }

    // Normalizar MAC: remover separadores e converter para uppercase
    const macClean = mac.replace(/[:\-\.]/g, '').toUpperCase()
    const macWithColons = macClean.match(/.{1,2}/g)?.join(':') || macClean

    // Buscar por ambos os formatos (com e sem separadores)
    const { data: device, error } = await supabase
        .from('devices')
        .select('*')
        .or(`mac_address.eq.${macClean},mac_address.eq.${macWithColons},mac_address.eq.${mac.toUpperCase()}`)
        .limit(1)
        .single()

    if (error || !device) {
        return NextResponse.json({
            mac: mac,
            sync_status: 'not_found',
            message: 'Dispositivo não cadastrado. Contate seu revendedor.'
        }, { status: 404 })
    }

    if (device.status === 'Bloqueado') {
        return NextResponse.json({
            mac: device.mac_address,
            sync_status: 'blocked',
            message: 'Dispositivo bloqueado. Contate seu revendedor.'
        }, { status: 403 })
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

// API pública que o app Android aconsulta para DELETAR o dispositivo pelo MAC
export async function DELETE(request, { params }) {
    const { mac } = await params

    if (!mac) {
        return NextResponse.json({ error: 'MAC address required' }, { status: 400 })
    }

    const macClean = mac.replace(/[:\-\.]/g, '').toUpperCase()
    const macWithColons = macClean.match(/.{1,2}/g)?.join(':') || macClean

    const { data, error } = await supabase
        .from('devices')
        .delete()
        .or(`mac_address.eq.${macClean},mac_address.eq.${macWithColons},mac_address.eq.${mac.toUpperCase()}`)
        .select()

    if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 })
    }

    if (!data || data.length === 0) {
        return NextResponse.json({ error: 'Supabase RLS bloqueou a exclusão ou dispositivo não existe.' }, { status: 403 })
    }

    return NextResponse.json({ success: true, message: 'Device deleted successfully' })
}
