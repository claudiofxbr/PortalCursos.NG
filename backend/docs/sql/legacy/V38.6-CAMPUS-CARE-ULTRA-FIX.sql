-- SCRIPT DE ESTABILIZAÇÃO DEFINITIVA V38.6-ULTRA
-- INSTRUÇÃO: COPIE TODO O TEXTO ABAIXO E COLE NO CONSOLE SQL DO NEON.

-- 1. Garante colunas de Auditoria na tabela principal
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_name VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;

-- 2. Sincroniza Soft Delete
UPDATE repair_tickets SET active = TRUE WHERE active IS NULL;

-- 3. Harmonização da Tabela de Fotos (ElementCollection)
-- Removemos inconsistências e garantimos o nome da coluna que o Java espera
DO $$ 
BEGIN
    -- Se a tabela de fotos existir, garantimos que ela tenha a coluna photo_url
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'repair_photos') THEN
        -- Se a coluna se chamar 'photos' (antigo), renomeamos para 'photo_url'
        IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'repair_photos' AND column_name = 'photos') THEN
            ALTER TABLE repair_photos RENAME COLUMN photos TO photo_url;
        END IF;
        
        -- Garante que photo_url seja TEXT para URLs longas
        ALTER TABLE repair_photos ALTER COLUMN photo_url TYPE TEXT;
    ELSE
        -- Se a tabela não existir, criamos a estrutura básica (o Hibernate faria isso, mas garantimos aqui)
        CREATE TABLE repair_photos (
            repair_ticket_id BIGINT NOT NULL,
            photo_url TEXT,
            FOREIGN KEY (repair_ticket_id) REFERENCES repair_tickets(id)
        );
    END IF;
END $$;

-- 4. Verificação Final da Saúde do Banco
SELECT 'COLUNAS DE AUDITORIA OK' as status
FROM information_schema.columns 
WHERE table_name = 'repair_tickets' AND column_name = 'reported_by_name';
