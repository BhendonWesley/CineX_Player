'use server'

import { getResellers } from '@/lib/adm-service'

/**
 * Server Action para buscar revendedores.
 * Nota: Em produção, o username e password seriam pegos da sessão segura do usuário logado.
 */
export async function fetchResellersAction() {
    // Para teste/demo, usando as credenciais que você forneceu.
    // No futuro, isso será dinâmico baseado no master logado.
    const username = 'cinetv'
    const password = '1020304050'

    try {
        const data = await getResellers(username, password)
        return { success: true, resellers: data }
    } catch (error) {
        return { success: false, message: 'Erro ao carregar revendedores.' }
    }
}
