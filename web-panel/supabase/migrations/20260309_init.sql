-- Create tables for CineX Web Panel
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    mac_address TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    status TEXT DEFAULT 'Ativo',
    reseller_id UUID REFERENCES auth.users(id),
    last_sync TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Playlists table (stores configuration for each device)
CREATE TABLE IF NOT EXISTS playlists (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
    type TEXT NOT NULL, -- 'm3u' or 'xtream'
    config JSONB NOT NULL, -- Stores URL or {dns, user, pass}
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Enable Row Level Security
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE playlists ENABLE ROW LEVEL SECURITY;

-- Policies
CREATE POLICY "Resellers can see their own devices" 
ON devices FOR SELECT 
USING (auth.uid() = reseller_id);

CREATE POLICY "Resellers can insert their own devices" 
ON devices FOR INSERT 
WITH CHECK (auth.uid() = reseller_id);

CREATE POLICY "Resellers can update their own devices" 
ON devices FOR UPDATE 
USING (auth.uid() = reseller_id);
