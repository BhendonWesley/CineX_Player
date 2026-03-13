'use server'

import { cookies } from 'next/headers'
import { supabase } from '@/lib/supabase'

function getUsername(cookieStore) {
    const session = cookieStore.get('cinex_session')
    if (!session) return null
    return JSON.parse(session.value).username
}

export async function getDevices() {
    const cookieStore = await cookies()
    const username = getUsername(cookieStore)
    if (!username) return { success: false, message: 'Não autenticado' }

    const { data, error } = await supabase
        .from('devices')
        .select('*')
        .eq('reseller_username', username)
        .order('created_at', { ascending: false })

    if (error) {
        console.error('Error fetching devices:', error)
        return { success: true, devices: [] }
    }
    return { success: true, devices: data || [] }
}

export async function addDevice(formData) {
    const cookieStore = await cookies()
    const username = getUsername(cookieStore)
    if (!username) return { success: false, message: 'Não autenticado' }

    const name = formData.get('name')
    const mac = formData.get('mac')
    const playlistType = formData.get('playlist_type')

    if (!name || !mac) {
        return { success: false, message: 'Nome e MAC são obrigatórios.' }
    }

    // Montar config da playlist
    let playlistConfig = {}
    if (playlistType === 'm3u') {
        playlistConfig = { url: formData.get('m3u_url') || '' }
    } else {
        playlistConfig = {
            dns: formData.get('xtream_dns') || '',
            user: formData.get('xtream_user') || '',
            pass: formData.get('xtream_pass') || '',
        }
    }

    const { data, error } = await supabase
        .from('devices')
        .insert({
            name,
            mac_address: mac.toUpperCase(),
            reseller_username: username,
            playlist_type: playlistType || 'xtream',
            playlist_config: playlistConfig,
            status: 'Ativo'
        })
        .select()

    if (error) {
        console.error('Error adding device:', error)
        if (error.code === '23505') {
            return { success: false, message: 'Este endereço MAC já está cadastrado.' }
        }
        return { success: false, message: 'Erro ao cadastrar dispositivo.' }
    }

    return { success: true, device: data?.[0] }
}

export async function deleteDevice(deviceId) {
    const cookieStore = await cookies()
    const username = getUsername(cookieStore)
    if (!username) return { success: false }

    const { data, error } = await supabase
        .from('devices')
        .delete()
        .eq('id', deviceId)
        .eq('reseller_username', username)
        .select()

    if (error) {
        console.error('Error deleting device:', error)
        return { success: false, message: `Erro DB: ${error.message}` }
    }

    if (!data || data.length === 0) {
        return { 
            success: false, 
            message: 'Erro no Banco: Permissão negada pelo Supabase. O Row Level Security (RLS) não possui política para Deletar em "devices".' 
        }
    }

    return { success: true }
}

export async function updateDevice(deviceId, formData) {
    const cookieStore = await cookies()
    const username = getUsername(cookieStore)
    if (!username) return { success: false }

    const name = formData.get('name')
    const playlistType = formData.get('playlist_type')

    let playlistConfig = {}
    if (playlistType === 'm3u') {
        playlistConfig = { url: formData.get('m3u_url') || '' }
    } else {
        playlistConfig = {
            dns: formData.get('xtream_dns') || '',
            user: formData.get('xtream_user') || '',
            pass: formData.get('xtream_pass') || '',
        }
    }

    const { error } = await supabase
        .from('devices')
        .update({
            name,
            playlist_type: playlistType,
            playlist_config: playlistConfig,
        })
        .eq('id', deviceId)
        .eq('reseller_username', username)

    if (error) {
        console.error('Error updating device:', error)
        return { success: false, message: 'Erro ao atualizar dispositivo.' }
    }
    return { success: true }
}

export async function toggleDeviceStatus(deviceId, newStatus) {
    const cookieStore = await cookies()
    const username = getUsername(cookieStore)
    if (!username) return { success: false }

    const { error } = await supabase
        .from('devices')
        .update({ status: newStatus })
        .eq('id', deviceId)
        .eq('reseller_username', username)

    if (error) {
        console.error('Error toggling device status:', error)
        return { success: false, message: 'Erro ao alterar status.' }
    }
    return { success: true }
}
