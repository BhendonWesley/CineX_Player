/**
 * CineX ADM Authentication Bridge
 * 
 * Este serviço valida as credenciais contra o painel oficial CineX TV.
 */

export async function checkResellerAccess(username, password) {
    const ADM_BASE_URL = 'https://painel.cinextv.com.br';
    const API_URL = `${ADM_BASE_URL}/sys/api.php`;

    try {
        // 1. Iniciar sessão para obter o Cookie (PHPSESSID)
        const initResponse = await fetch(ADM_BASE_URL);
        const setCookie = initResponse.headers.get('set-cookie');
        const phpSessId = setCookie ? setCookie.split(';')[0] : '';

        // 2. Tentar o Login via API do painel
        // O painel usa action=login&username=...&password=...
        const formData = new URLSearchParams();
        formData.append('action', 'login');
        formData.append('username', username);
        formData.append('password', password);

        const loginResponse = await fetch(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
                'Cookie': phpSessId
            },
            body: formData.toString(),
        });

        const loginResult = await loginResponse.json();

        // No sistema CineX TV, se o login for bem-ucedido, ele retorna um status positivo
        // ou redireciona. Vamos checar se o loginResult indica sucesso.
        // Baseado na análise, se não houver erro no JSON, prosseguimos para validar o Dashboard.

        if (loginResult.status === 'success' || loginResult.link) {
            // 3. Validação Final: Tentar acessar o Dashboard para confirmar que é uma Revenda
            const dashboardResponse = await fetch(`${ADM_BASE_URL}/dashboard`, {
                headers: { 'Cookie': phpSessId }
            });
            const dashboardHtml = await dashboardResponse.text();

            // Checamos se o HTML contém elementos de um painel logado
            if (dashboardHtml.includes('Dashboard') || dashboardHtml.includes(username)) {
                return {
                    authorized: true,
                    profile: {
                        username,
                        role: 'reseller',
                        name: username.toUpperCase()
                    }
                };
            }
        }

        return {
            authorized: false,
            message: 'Credenciais inválidas ou conta não autorizada como Revendedor CineX.'
        };

    } catch (error) {
        console.error('Erro na Bridge ADM:', error);
        return {
            authorized: false,
            message: 'Falha na conexão com o sistema CineX TV. Verifique seu servidor.'
        };
    }
}

/**
 * Busca a lista de revendedores diretamente do painel ADM
 */
export async function getResellers(username, password) {
    const ADM_BASE_URL = 'https://painel.cinextv.com.br';
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
