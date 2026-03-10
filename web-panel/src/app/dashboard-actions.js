'use server'

import { cookies } from 'next/headers'
import { supabase } from '@/lib/supabase'

export async function getDashboardData() {
    try {
        const cookieStore = await cookies()
        const session = cookieStore.get('cinex_session')
        if (!session) return { success: false }

        const profile = JSON.parse(session.value)
        const username = profile.username

        // Buscar dispositivos do revendedor
        const { data: devices, error } = await supabase
            .from('devices')
            .select('*')
            .eq('reseller_username', username)
            .order('created_at', { ascending: false })

        if (error) {
            console.error('Supabase error:', error)
            return {
                success: true,
                username,
                totalDevices: 0,
                activeDevices: 0,
                inactiveDevices: 0,
                recentDevices: []
            }
        }

        const activeDevices = devices?.filter(d => d.status === 'Ativo').length || 0
        const inactiveDevices = (devices?.length || 0) - activeDevices

        return {
            success: true,
            username,
            totalDevices: devices?.length || 0,
            activeDevices,
            inactiveDevices,
            recentDevices: devices?.slice(0, 5) || []
        }
    } catch (error) {
        console.error('Dashboard error:', error)
        return { success: false }
    }
}
