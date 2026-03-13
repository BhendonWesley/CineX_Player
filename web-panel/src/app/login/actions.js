'use server'

import { checkResellerAccess } from '@/lib/adm-service'
import { cookies } from 'next/headers'
import { addDebugLog as log } from '@/lib/debug-logger'
import { redirect } from 'next/navigation'

export async function loginAction(formData) {
    const username = formData.get('username')
    const password = formData.get('password')

    if (!username || !password) {
        return { success: false, message: 'Preencha todos os campos.' }
    }

    try {
        const result = await checkResellerAccess(username, password)

        if (result.authorized) {
            log('ADM LOGIN SUCCESS: ' + username);
            console.error(' >>> [ACTION] LOGIN SUCCESS FOR:', username);
            
            (await cookies()).set('cinex_session', JSON.stringify(result.profile), {
                httpOnly: true,
                secure: false, 
                sameSite: 'lax',
                maxAge: 60 * 60 * 24, // 24h
                path: '/',
                priority: 'high', // Added based on instruction
            });

            // Cookie de backup visível ao cliente para garantir persistência no navegador
            (await cookies()).set('cinex_login_checkpoint', result.profile.username, {
                httpOnly: false,
                secure: false,
                sameSite: 'lax',
                maxAge: 60 * 60 * 24,
                path: '/',
            });

            // Chama o redirecionamento - isso joga uma exceção que o Next.js captura
            redirect('/');
        } else {
            log('ADM LOGIN FAILED: ' + username + ' | ' + result.message);
            return { success: false, message: result.message }
        }
    } catch (error) {
        // Se for um erro de redirecionamento, joga ele pra frente para o Next.js tratar
        if (error.digest?.startsWith('NEXT_REDIRECT')) throw error;

        log('Login action error: ' + error.message);
        return { success: false, message: 'Erro interno ao processar login.' }
    }
}
