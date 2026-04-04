-- CineX TMDB Cache — compartilha metadados TMDB entre todos os dispositivos
-- Chave: tipo normalizado do conteúdo (ex: "movie:barbie 2023", "tv:breaking bad")
-- Isso garante que o cache funcione mesmo para clientes de servidores IPTV diferentes

CREATE TABLE IF NOT EXISTS tmdb_cache (
    cache_key TEXT PRIMARY KEY,
    poster_url TEXT,
    banner_url TEXT,
    synopsis TEXT,
    rating FLOAT,
    year TEXT,
    trailer_url TEXT,
    cast_members TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index para limpeza de cache expirado
CREATE INDEX IF NOT EXISTS idx_tmdb_cache_updated ON tmdb_cache(updated_at);

-- Permitir leitura/escrita pública (dados TMDB são públicos)
ALTER TABLE tmdb_cache ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read on tmdb_cache"
    ON tmdb_cache FOR SELECT
    USING (true);

CREATE POLICY "Allow public insert on tmdb_cache"
    ON tmdb_cache FOR INSERT
    WITH CHECK (true);

CREATE POLICY "Allow public update on tmdb_cache"
    ON tmdb_cache FOR UPDATE
    USING (true);
