-- ---------------------------------------------------------
-- PROTOCOLO V30.9-SUPREME: CORRECÃO DE BANCO DE DADOS
-- OBJETIVO: GARANTIR INTEGRIDADE DE FOTOS E PERFORMANCE
-- ---------------------------------------------------------

-- 1. Garantir que a coluna foto_matricula existe na tabela students (Graduação)
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students ADD COLUMN foto_matricula VARCHAR(255);
    END IF;
END $$;

-- 2. Garantir que a coluna foto_url existe na tabela postgrad_students (Pós-Graduação)
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_url') THEN
        ALTER TABLE postgrad_students ADD COLUMN foto_url VARCHAR(255);
    END IF;
END $$;

-- 3. Índices de Performance para Auditoria e Busca Rápida
CREATE INDEX IF NOT EXISTS idx_students_full_name ON students (full_name);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_full_name ON postgrad_students (full_name);
CREATE INDEX IF NOT EXISTS idx_user_sessions_token ON user_sessions (refresh_token);

-- 4. Limpeza de sessões órfãs (Manutenção Preventiva)
DELETE FROM user_sessions WHERE expiry_date < NOW();

-- FIM DO PROTOCOLO SUPREME-DATABASE-FIX
