-- V2__Consolidate_Schema_And_Indexes.sql
-- Consolidação compatível com esquema legado (creator_id em vez de creator_name inline)

-- 1. Auditoria Estendida — colunas que podem não existir
ALTER TABLE students        ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE users           ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE users           ADD COLUMN IF NOT EXISTS foto_url VARCHAR(255);
ALTER TABLE users           ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users           ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 2. Colunas de auditoria em students (banco legado sem created_at)
ALTER TABLE students ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE students ADD COLUMN IF NOT EXISTS creator_name VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS creator_photo_url TEXT;

-- 3. Colunas de auditoria em postgrad_students
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS creator_name VARCHAR(255);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS creator_photo_url TEXT;

-- 4. Campus Care (Reparos)
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS resolved_at       TIMESTAMP;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_id    BIGINT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role  VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS last_modified_by  VARCHAR(100);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_name  VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 5. Normalização de repair_photos
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'repair_photos' AND column_name = 'photos') THEN
        ALTER TABLE repair_photos RENAME COLUMN photos TO photo_url;
    END IF;
END $$;

-- 6. Índices de performance (todos IF NOT EXISTS para ser idempotente)
CREATE INDEX IF NOT EXISTS idx_repair_status      ON repair_tickets(status);
CREATE INDEX IF NOT EXISTS idx_repair_active       ON repair_tickets(active);
CREATE INDEX IF NOT EXISTS idx_students_created_at  ON students(created_at);
CREATE INDEX IF NOT EXISTS idx_postgrad_created_at  ON postgrad_students(created_at);
CREATE INDEX IF NOT EXISTS idx_repairs_created_at   ON repair_tickets(created_at);

-- 7. Atualiza estatísticas do query planner
ANALYZE;
