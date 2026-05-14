-- V2__Consolidate_Schema_And_Indexes.sql
-- Consolidação de correções e otimização de índices

-- 1. Auditoria Estendida
ALTER TABLE students ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

-- 2. Garantir integridade Campus Care (Reparos)
-- O script V39.0-ULTRA indicou que estas colunas eram essenciais
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS resolved_at      TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_id   BIGINT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

-- 3. Normalização de repair_photos
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'repair_photos' AND column_name = 'photos') THEN
        ALTER TABLE repair_photos RENAME COLUMN photos TO photo_url;
    END IF;
END $$;

-- 4. Otimização de Índices (Removendo redundâncias e adicionando buscas frequentes)
-- Nota: username e email já possuem índices automáticos por serem UNIQUE.

-- Performance em Reparos
CREATE INDEX IF NOT EXISTS idx_repair_status ON repair_tickets(status);
CREATE INDEX IF NOT EXISTS idx_repair_active ON repair_tickets(active);

-- Performance em Estudantes
CREATE INDEX IF NOT EXISTS idx_student_registration ON students(registration_number);
CREATE INDEX IF NOT EXISTS idx_postgrad_registration ON postgrad_students(registration_number);

-- Performance em Auditoria (Busca por data)
CREATE INDEX IF NOT EXISTS idx_students_created_at ON students(created_at);
CREATE INDEX IF NOT EXISTS idx_postgrad_created_at ON postgrad_students(created_at);
CREATE INDEX IF NOT EXISTS idx_repairs_created_at ON repair_tickets(created_at);

-- 5. ANALYZE para atualizar estatísticas do query planner
ANALYZE;
