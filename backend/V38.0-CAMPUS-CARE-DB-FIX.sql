-- V38.0-CAMPUS-CARE-DB-FIX.sql
-- PROTOCOLO V38.0 - ESTABILIZAÇÃO FINAL DO BANCO DE DADOS (CAMPUS CARE)
-- OBJETIVO: Corrigir a "Falha Sistêmica" de registro adicionando colunas faltantes.

DO $$ 
BEGIN 
    -- 1. Garante colunas de Auditoria Biométrica na tabela repair_tickets
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='creator_name') THEN 
        ALTER TABLE repair_tickets ADD COLUMN creator_name VARCHAR(255); 
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='creator_position') THEN 
        ALTER TABLE repair_tickets ADD COLUMN creator_position VARCHAR(255); 
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='creator_photo_url') THEN 
        ALTER TABLE repair_tickets ADD COLUMN creator_photo_url TEXT; 
    END IF;

    -- 2. Garante suporte a Soft Delete
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='active') THEN 
        ALTER TABLE repair_tickets ADD COLUMN active BOOLEAN DEFAULT TRUE; 
    END IF;

    -- 3. Correção de integridade para fotos principais
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='main_photo_url') THEN 
        ALTER TABLE repair_tickets ADD COLUMN main_photo_url TEXT; 
    END IF;

    -- 4. Índice de performance para auditoria
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_repair_audit') THEN
        CREATE INDEX idx_repair_audit ON repair_tickets(creator_name, creator_position);
    END IF;

    -- 5. Tabela de evidências fotográficas (ElementCollection)
    CREATE TABLE IF NOT EXISTS repair_photos (
        repair_ticket_id BIGINT NOT NULL,
        photo_url VARCHAR(255),
        CONSTRAINT fk_repair_ticket FOREIGN KEY (repair_ticket_id) REFERENCES repair_tickets(id) ON DELETE CASCADE
    );

    RAISE NOTICE 'Banco de dados sincronizado com Protocolo V38.0-ULTRA.';
END $$;
