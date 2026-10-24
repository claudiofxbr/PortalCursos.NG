-- SCRIPT DE INFRAESTRUTURA V38.1-ULTRA (PROTOCOLO CAMPUS CARE)
-- Este script deve ser COPIADO e COLADO no console SQL do Neon.
-- Não tente rodar o comando "backend/V..." direto no console.

-- 1. Garante que as colunas de auditoria e Soft Delete existam sem causar erros de interrupção
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_name VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;

-- 2. Sincroniza registros legados para evitar o "Erro ao carregar chamados" (Soft Delete Filter)
UPDATE repair_tickets SET active = TRUE WHERE active IS NULL;

-- 3. Garante que a tabela repair_photos (ElementCollection) esteja vinculada
-- Nota: Esta tabela é gerada automaticamente pelo JPA, mas garantimos os tipos aqui
DO $$ 
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'repair_photos') THEN
        ALTER TABLE repair_photos ALTER COLUMN photos TYPE TEXT;
    END IF;
END $$;

-- VERIFICAÇÃO FINAL:
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'repair_tickets' 
AND column_name IN ('active', 'reported_by_name', 'reported_by_role', 'reporter_photo_url');
