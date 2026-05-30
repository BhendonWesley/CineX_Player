/**
 * CineX ADM Authentication Bridge
 * 
 * Este serviço valida as credenciais contra o painel oficial CineX TV.
 */

export async function checkResellerAccess(username, password) {
    const ADM_BASE_URL = 'https://painell.cinextv.com.br';
    const API_URL = `${ADM_BASE_URL}/sys/api.php`;

    try {
        // 1. Tentar login direto na API do painel (mesmo endpoint que os revendedores usam)
        const formData = new URLSearchParams();
        formData.append('action', 'login');
        formData.append('username', username);
        formData.append('password', password);
        formData.append('recaptcha', '');

        const loginResponse = await fetch(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: formData.toString(),
        });

        const loginResult = await loginResponse.json();

        // DEBUG TEMPORÁRIO - remover após identificar o formato da resposta
        console.log('API Response Status:', loginResponse.status);
        console.log('API Response Body:', JSON.stringify(loginResult));

        // A API retorna { success: true } quando as credenciais são válidas
        if (loginResult.success === true) {
            return {
                authorized: true,
                profile: {
                    username,
                    role: 'reseller',
                    name: username.toUpperCase()
                }
            };
        }

        // Mensagens de erro específicas do painel
        const errorMessages = {
            'invalid_user_or_pass': 'Usuário ou senha incorretos.',
            'blocked': 'Sua conta está bloqueada. Contate o administrador.',
            'insufficient_permission': 'Você não tem permissão para acessar este painel.',
            'empty_user_or_pass': 'Preencha todos os campos.',
        };

        return {
            authorized: false,
            message: errorMessages[loginResult.message] || 'Credenciais inválidas ou conta não autorizada como Revendedor CineX.'
        };

    } catch (error) {
        console.error('Erro na Bridge ADM:', error);
        return {
            authorized: false,
            message: 'Falha na conexão com o sistema CineX TV. Tente novamente.'
        };
    }
}


/**
 * Busca a lista de revendedores diretamente do painel ADM
 */
export async function getResellers(username, password) {
    const ADM_BASE_URL = 'https://painell.cinextv.com.br';
    const API_URL = `${ADM_BASE_URL}/sys/api.php?action=get_resellers&length=100`;

    try {
        // 1. Login para obter sessão (PHPSESSID)
        const initResponse = await fetch(ADM_BASE_URL);
        const setCookie = initResponse.headers.get('set-cookie');
        const phpSessId = setCookie ? setCookie.split(';')[0] : '';

        const formData = new URLSearchParams();
        formData.append('action', 'login');
        formData.append('username', username);
        formData.append('password', password);

        await fetch(`${ADM_BASE_URL}/sys/api.php`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
                'Cookie': phpSessId
            },
            body: formData.toString()
        });

        // 2. Buscar a lista de revendedores
        const response = await fetch(API_URL, {
            headers: { 'Cookie': phpSessId }
        });

        const result = await response.json();
        return result.data || [];

    } catch (error) {
        console.error('Erro ao buscar revendedores no ADM:', error);
        return [];
    }
}
