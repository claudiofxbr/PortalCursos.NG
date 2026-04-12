-- ---------------------------------------------------------
-- PROTOCOLO V31.4-ULTRA: SCRIPT DEFINITIVO DE SCHEMA
-- DATA: 12/04/2026
-- OBJETIVO: GARANTIR ESTABILIDADE EM PÓS-GRADUAÇÃO
-- ---------------------------------------------------------

-- 1. Estabilização da Tabela postgrad_students
DO $$ 
BEGIN 
    -- Coluna de Visibilidade (Soft Delete)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='active') THEN
        ALTER TABLE postgrad_students ADD COLUMN active BOOLEAN DEFAULT TRUE;
    END IF;

    -- Colunas de Auditoria MEC
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='creator_name') THEN
        ALTER TABLE postgrad_students ADD COLUMN creator_name VARCHAR(255);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='creator_position') THEN
        ALTER TABLE postgrad_students ADD COLUMN creator_position VARCHAR(255);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='creator_photo_url') THEN
        ALTER TABLE postgrad_students ADD COLUMN creator_photo_url TEXT;
    END IF;

    -- Padronização de Foto de Matrícula
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_url') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN
        ALTER TABLE postgrad_students RENAME COLUMN foto_url TO foto_matricula;
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN
        ALTER TABLE postgrad_students ADD COLUMN foto_matricula VARCHAR(255);
    END IF;
END $$;

-- 2. Atualização Forçada de Visibilidade
UPDATE postgrad_students SET active = TRUE WHERE active IS NULL;

-- 3. Replicação de Robustez para Graduação (students)
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='active') THEN
        ALTER TABLE students ADD COLUMN active BOOLEAN DEFAULT TRUE;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_url') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students RENAME COLUMN foto_url TO foto_matricula;
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students ADD COLUMN foto_matricula VARCHAR(255);
    END IF;
END $$;

UPDATE students SET active = TRUE WHERE active IS NULL;

-- FIM DO PROTOCOLO V31.4-ULTRA
