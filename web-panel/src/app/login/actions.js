'use server'

import { checkResellerAccess } from '@/lib/adm-service'
import { cookies } from 'next/headers'

export async function loginAction(formData) {
    const username = formData.get('username')
    const password = formData.get('password')

    if (!username || !password) {
        return { success: false, message: 'Preencha todos os campos.' }
    }

    try {
        const result = await checkResellerAccess(username, password)

        if (result.authorized) {
            // Configurar Cookie de sessão (expira em 24h)
            (await cookies()).set('cinex_session', JSON.stringify(result.profile), {
                httpOnly: true,
                secure: process.env.NODE_ENV === 'production',
                maxAge: 60 * 60 * 24,
                path: '/',
            })

            return { success: true, profile: result.profile }
        } else {
            return { success: false, message: result.message }
        }
    } catch (error) {
        console.error('Login action error:', error)
        return { success: false, message: 'Erro interno ao processar login.' }
    }
}
