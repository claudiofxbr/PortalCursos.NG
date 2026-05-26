-- =============================================================================
-- SCRIPT DE COMPLEMENTO DE AUDITORIA E INTEGRIDADE (V5) - PORTALCURSOS.NG
-- Versão: 5.0 (Flyway Migration)
-- Objetivo: Garantir a presença de TODAS as colunas de BaseAuditEntity em todas as tabelas filhas
-- =============================================================================

BEGIN;

-- 1. Adicionar colunas básicas de auditoria (IF NOT EXISTS) para students
ALTER TABLE students ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE students ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE students ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE students ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE students ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP;
ALTER TABLE students ADD COLUMN IF NOT EXISTS version BIGINT;

-- 2. Adicionar colunas básicas de auditoria (IF NOT EXISTS) para staff_members
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP;
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS version BIGINT;

-- 3. Adicionar colunas básicas de auditoria (IF NOT EXISTS) para repair_tickets
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS version BIGINT;

-- 4. Adicionar colunas básicas de auditoria (IF NOT EXISTS) para payments
ALTER TABLE payments ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT;

-- 5. Adicionar colunas básicas de auditoria (IF NOT EXISTS) para courses
ALTER TABLE courses ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS version BIGINT;

-- 6. Bloco seguro para garantir colunas creator_id com Foreign Keys
DO $$ 
BEGIN 
    -- students
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='creator_id') THEN
        ALTER TABLE students ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;

    -- staff_members
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='staff_members' AND column_name='creator_id') THEN
        ALTER TABLE staff_members ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;

    -- repair_tickets
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='creator_id') THEN
        ALTER TABLE repair_tickets ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;

    -- payments
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='creator_id') THEN
        ALTER TABLE payments ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;

    -- courses
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_id') THEN
        ALTER TABLE courses ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;
END $$;

-- 7. Criar índices para otimização de performance
CREATE INDEX IF NOT EXISTS idx_payments_creator ON payments(creator_id);
CREATE INDEX IF NOT EXISTS idx_staff_members_creator ON staff_members(creator_id);

COMMIT;
