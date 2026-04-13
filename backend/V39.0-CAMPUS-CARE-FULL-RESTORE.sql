-- ================================================================
-- PROTOCOLO V39.0-ULTRA: RESTAURAÇÃO COMPLETA CAMPUS CARE
-- EXECUTAR ESTE SCRIPT NO CONSOLE SQL DO NEON ANTES DE REINICIAR
-- ================================================================

-- ----------------------------------------------------------------
-- ETAPA 1: Garantir TODAS as colunas da tabela repair_tickets
-- ----------------------------------------------------------------
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS title            VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS description      TEXT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS location         VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS status           VARCHAR(50) DEFAULT 'OPEN';
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS main_photo_url   TEXT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS created_at       TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS resolved_at      TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_id   BIGINT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_name VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS active           BOOLEAN NOT NULL DEFAULT TRUE;

-- ----------------------------------------------------------------
-- ETAPA 2: Corrigir registros com valores críticos nulos
-- ----------------------------------------------------------------
UPDATE repair_tickets SET active     = TRUE    WHERE active IS NULL;
UPDATE repair_tickets SET status     = 'OPEN'  WHERE status IS NULL OR status = '';
UPDATE repair_tickets SET created_at = NOW()   WHERE created_at IS NULL;

-- ----------------------------------------------------------------
-- ETAPA 3: Criar tabela repair_photos SE NÃO EXISTIR
-- (causa raiz mais comum do HTTP 500 no GET /repairs)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS repair_photos (
    repair_ticket_id BIGINT NOT NULL,
    photo_url        TEXT,
    CONSTRAINT fk_repair_photos_ticket
        FOREIGN KEY (repair_ticket_id)
        REFERENCES repair_tickets(id)
        ON DELETE CASCADE
);

-- ----------------------------------------------------------------
-- ETAPA 4: Normalizar schema legacy da tabela repair_photos
-- ----------------------------------------------------------------
DO $$
BEGIN
    -- Renomeia coluna legada 'photos' → 'photo_url' se existir
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'repair_photos' AND column_name = 'photos'
    ) THEN
        ALTER TABLE repair_photos RENAME COLUMN photos TO photo_url;
        RAISE NOTICE 'Coluna "photos" renomeada para "photo_url" em repair_photos.';
    END IF;

    -- Garante que photo_url é TEXT (aceita URLs longas)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'repair_photos' AND column_name = 'photo_url'
          AND data_type <> 'text'
    ) THEN
        ALTER TABLE repair_photos ALTER COLUMN photo_url TYPE TEXT;
        RAISE NOTICE 'Coluna photo_url convertida para TEXT.';
    END IF;

    -- Garante que a coluna repair_ticket_id existe
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'repair_photos' AND column_name = 'repair_ticket_id'
    ) THEN
        ALTER TABLE repair_photos ADD COLUMN repair_ticket_id BIGINT NOT NULL;
        RAISE NOTICE 'Coluna repair_ticket_id adicionada em repair_photos.';
    END IF;
END $$;

-- ----------------------------------------------------------------
-- ETAPA 5: Verificação Final — deve listar todas as colunas abaixo
-- ----------------------------------------------------------------
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'repair_tickets'
ORDER BY ordinal_position;

-- Verificar tabela de fotos
SELECT
    column_name,
    data_type
FROM information_schema.columns
WHERE table_name = 'repair_photos'
ORDER BY ordinal_position;
