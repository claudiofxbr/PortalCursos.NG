-- ---------------------------------------------------------
-- PROTOCOLO V30.9-SUPREME: CORRECÃO DE BANCO DE DADOS
-- OBJETIVO: GARANTIR INTEGRIDADE DE FOTOS E PERFORMANCE
-- ---------------------------------------------------------

-- 1. Matriz de Graduação (students)
DO $$ 
BEGIN 
    -- Se existir foto_url, renomeia para foto_matricula
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_url') THEN
        ALTER TABLE students RENAME COLUMN foto_url TO foto_matricula;
    -- Se não existir nenhuma das duas, cria foto_matricula
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students ADD COLUMN foto_matricula VARCHAR(255);
    END IF;
END $$;

-- 2. Matriz de Pós-Graduação (postgrad_students)
DO $$ 
BEGIN 
    -- Se existir foto_url, renomeia para foto_matricula
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_url') THEN
        ALTER TABLE postgrad_students RENAME COLUMN foto_url TO foto_matricula;
    -- Se não existir nenhuma das duas, cria foto_matricula
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN
        ALTER TABLE postgrad_students ADD COLUMN foto_matricula VARCHAR(255);
    END IF;
END $$;

-- 3. Índices de Performance para Auditoria e Busca Rápida
CREATE INDEX IF NOT EXISTS idx_students_full_name ON students (full_name);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_full_name ON postgrad_students (full_name);
CREATE INDEX IF NOT EXISTS idx_user_sessions_token ON user_sessions (refresh_token);

-- 4. Limpeza de sessões órfãs (Manutenção Preventiva)
DELETE FROM user_sessions WHERE expiry_date < NOW();

-- FIM DO PROTOCOLO SUPREME-DATABASE-FIX
