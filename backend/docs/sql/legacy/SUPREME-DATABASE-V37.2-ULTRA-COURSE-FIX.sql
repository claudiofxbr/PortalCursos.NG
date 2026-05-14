-- =============================================
-- POLÍTICA DE AUDITORIA MEC - PROTOCOLO V37.2-ULTRA-CERTIFIED
-- OBJETIVO: Sincronização Absoluta de Auditoria e Fotos para GESTÃO DE CURSOS
-- DATA: 13/04/2026
-- =============================================

DO $$ 
BEGIN 
    -- 1. Sincronização de Schema para a Tabela COURSES (MEC Audit)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='active') THEN 
        ALTER TABLE courses ADD COLUMN active BOOLEAN DEFAULT TRUE; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_name') THEN 
        ALTER TABLE courses ADD COLUMN creator_name VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_position') THEN 
        ALTER TABLE courses ADD COLUMN creator_position VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_photo_url') THEN 
        ALTER TABLE courses ADD COLUMN creator_photo_url TEXT; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='created_at') THEN 
        ALTER TABLE courses ADD COLUMN created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='updated_at') THEN 
        ALTER TABLE courses ADD COLUMN updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='registration_date') THEN 
        ALTER TABLE courses ADD COLUMN registration_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP; 
    END IF;

    -- 2. Garantir que a tabela STAFF_MEMBERS possua a coluna de foto (Fonte da Auditoria)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='staff_members' AND column_name='foto_url') THEN 
        ALTER TABLE staff_members ADD COLUMN foto_url TEXT; 
    END IF;

    -- 3. Otimização de Índices para Performance de Auditoria
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_courses_active') THEN
        CREATE INDEX idx_courses_active ON courses(active);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_courses_creator') THEN
        CREATE INDEX idx_courses_creator ON courses(creator_name);
    END IF;

END $$;

-- 4. Normalização e Comentários Técnicos
UPDATE courses SET active = TRUE WHERE active IS NULL;

COMMENT ON COLUMN courses.creator_name IS 'Nome do staff responsável pelo cadastro do curso (Audit Standard)';
COMMENT ON COLUMN courses.creator_position IS 'Cargo do emissor do registro';
COMMENT ON COLUMN courses.creator_photo_url IS 'Foto do responsável injetada no momento do cadastro';
COMMENT ON COLUMN courses.registration_date IS 'Data oficial de registro institucional';

-- Verificação Final
SELECT 'MEC_AUDIT_OK' as status, table_name, column_name 
FROM information_schema.columns 
WHERE table_name = 'courses' 
AND column_name IN ('creator_name', 'creator_photo_url', 'registration_date');
