-- CineX Web Panel - Schema Atualizado
-- Usa reseller_username (string) em vez de auth.users para compatibilidade com login externo

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    mac_address TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    reseller_username TEXT NOT NULL,
    playlist_type TEXT DEFAULT 'xtream',
    playlist_config JSONB DEFAULT '{}',
    status TEXT DEFAULT 'Ativo',
    last_sync TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index para busca rápida por MAC (usado pelo app Android)
CREATE INDEX IF NOT EXISTS idx_devices_mac ON devices(mac_address);

-- Index para busca por revendedor (usado pelo painel web)
CREATE INDEX IF NOT EXISTS idx_devices_reseller ON devices(reseller_username);
