
require('dotenv').config({ path: '.env.local' });
const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

async function checkDevices(username) {
    const { data, error, count } = await supabase
        .from('devices')
        .select('*', { count: 'exact' })
        .eq('reseller_username', username);

    if (error) {
        console.error('Error:', error);
        return;
    }

    console.log(`User: ${username}`);
    console.log(`Exact Count: ${count}`);
    console.log(`Data Length: ${data.length}`);
    console.log('Devices:', data.map(d => ({ mac: d.mac_address, status: d.status })));
}

const targetUser = process.argv[2] || 'BhendonWesley';
checkDevices(targetUser);
