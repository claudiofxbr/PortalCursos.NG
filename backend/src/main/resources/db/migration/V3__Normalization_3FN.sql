-- V3__Normalization_3FN.sql
-- Normalização 3FN: centraliza auditoria em creator_id -> staff_members
-- Compatível com esquema legado (courses já sem creator_name/creator_position)

-- 1. Garantir creator_id nas tabelas de negócio
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_id') THEN
        ALTER TABLE courses ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='creator_id') THEN
        ALTER TABLE students ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='creator_id') THEN
        ALTER TABLE repair_tickets ADD COLUMN creator_id BIGINT REFERENCES staff_members(id);
    END IF;
END $$;

-- 2. Migração de dados (melhor esforço, sem falhar se coluna não existir)
DO $$
BEGIN
    -- courses: tentar migrar via creator_name se ainda existir
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_name') THEN
        UPDATE courses c SET creator_id = s.id FROM staff_members s
        WHERE c.creator_name = s.full_name AND c.creator_id IS NULL;
    END IF;

    -- repair_tickets: usar reported_by_id como creator_id se existir
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='reported_by_id') THEN
        UPDATE repair_tickets rt SET creator_id = reported_by_id
        WHERE creator_id IS NULL AND reported_by_id IS NOT NULL;
    END IF;
END $$;

-- 3. Remover colunas redundantes de courses (já podem não existir)
ALTER TABLE courses DROP COLUMN IF EXISTS creator_name;
ALTER TABLE courses DROP COLUMN IF EXISTS creator_position;
ALTER TABLE courses DROP COLUMN IF EXISTS creator_photo_url;

-- 4. Remover colunas legadas de students (se existirem)
ALTER TABLE students DROP COLUMN IF EXISTS created_by_name;
ALTER TABLE students DROP COLUMN IF EXISTS created_by_position;

-- 5. Índices de performance para joins de auditoria
CREATE INDEX IF NOT EXISTS idx_courses_creator  ON courses(creator_id);
CREATE INDEX IF NOT EXISTS idx_students_creator ON students(creator_id);
CREATE INDEX IF NOT EXISTS idx_repairs_creator  ON repair_tickets(creator_id);
