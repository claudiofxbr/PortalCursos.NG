-- ============================================================
-- SCRIPT DEFINITIVO V38.8-ULTRA: SINCRONIZAÇÃO CAMPUS CARE
-- INSTRUÇÃO: COPIE TUDO ABAIXO E COLE NO CONSOLE SQL DO NEON
-- ============================================================

-- Garante que a tabela tenha TODAS as colunas que o Java espera
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_name VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role  VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;

-- Ativa todos os registros existentes para o Soft Delete não sumir nada
UPDATE repair_tickets SET active = TRUE WHERE active IS NULL;

-- Verifica a tabela de fotos e garante o schema correto
DO $$ 
BEGIN
    -- Se a coluna se chama 'photos' (nome antigo), renomeia
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'repair_photos' AND column_name = 'photos'
    ) THEN
        ALTER TABLE repair_photos RENAME COLUMN photos TO photo_url;
    END IF;
    
    -- Garante que photo_url aceite URLs longas
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'repair_photos' AND column_name = 'photo_url'
    ) THEN
        ALTER TABLE repair_photos ALTER COLUMN photo_url TYPE TEXT;
    END IF;
END $$;

-- CONFIRMAÇÃO FINAL: deve retornar todas as colunas abaixo
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'repair_tickets'
  AND column_name IN ('active','reported_by_name','reported_by_role','reporter_photo_url')
ORDER BY column_name;
