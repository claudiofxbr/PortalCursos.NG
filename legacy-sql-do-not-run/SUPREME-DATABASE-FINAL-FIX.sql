-- [SUPREME-DATABASE-FINAL-FIX.sql]
-- PROTOCOLO V31.4-ULTRA - ESTABILIZAÇÃO DE VISIBILIDADE

-- 1. GARANTIR COLUNA ACTIVE NA TABELA STUDENTS
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='active') THEN
        ALTER TABLE students ADD COLUMN active BOOLEAN DEFAULT TRUE;
    END IF;
END $$;

-- 2. GARANTIR COLUNA ACTIVE NA TABELA POSTGRAD_STUDENTS
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='active') THEN
        ALTER TABLE postgrad_students ADD COLUMN active BOOLEAN DEFAULT TRUE;
    END IF;
END $$;

-- 3. RENOMEAR FOTO_URL PARA FOTO_MATRICULA SE NECESSÁRIO (STUDENTS)
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_url') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students RENAME COLUMN foto_url TO foto_matricula;
    END IF;
END $$;

-- 4. RENOMEAR FOTO_URL PARA FOTO_MATRICULA SE NECESSÁRIO (POSTGRAD_STUDENTS)
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_url') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN
        ALTER TABLE postgrad_students RENAME COLUMN foto_url TO foto_matricula;
    END IF;
END $$;

-- 5. UPDATE DE VISIBILIDADE (FORÇAR TRUE EM REGISTROS EXISTENTES)
UPDATE students SET active = TRUE WHERE active IS NULL OR active = FALSE;
UPDATE postgrad_students SET active = TRUE WHERE active IS NULL OR active = FALSE;

-- 6. VERIFICAÇÃO DE STAFF (CERTIFICAR QUE FOTO_URL EXISTE LÁ)
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='staff_members' AND column_name='foto_url') THEN
        ALTER TABLE staff_members ADD COLUMN foto_url VARCHAR(255);
    END IF;
END $$;

-- SCRIPT CONCLUÍDO - PROTOCOLO V31.4-ULTRA
