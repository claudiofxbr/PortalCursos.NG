-- =============================================
-- PROTOCOLO V32.0-ULTRA-SUPREME-FINAL (MEC)
-- OBJETIVO: Sincronização Absoluta de Auditoria e Fotos
-- ALVO: Neon PostgreSQL (PortalCursos.NG)
-- =============================================

DO $$ 
BEGIN 
    -- 1. Auditoria e Fotos para STUDENTS (Graduação)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='active') THEN 
        ALTER TABLE students ADD COLUMN active BOOLEAN DEFAULT TRUE; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='creator_name') THEN 
        ALTER TABLE students ADD COLUMN creator_name VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='creator_position') THEN 
        ALTER TABLE students ADD COLUMN creator_position VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='creator_photo_url') THEN 
        ALTER TABLE students ADD COLUMN creator_photo_url TEXT; 
    END IF;
    -- Garantir foto_matricula (usada no JPA Student.java)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN 
        ALTER TABLE students ADD COLUMN foto_matricula TEXT; 
    END IF;

    -- 2. Auditoria e Fotos para POSTGRAD_STUDENTS (Pós-Graduação)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='active') THEN 
        ALTER TABLE postgrad_students ADD COLUMN active BOOLEAN DEFAULT TRUE; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='creator_name') THEN 
        ALTER TABLE postgrad_students ADD COLUMN creator_name VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='creator_position') THEN 
        ALTER TABLE postgrad_students ADD COLUMN creator_position VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='creator_photo_url') THEN 
        ALTER TABLE postgrad_students ADD COLUMN creator_photo_url TEXT; 
    END IF;
    -- Garantir foto_matricula para Pós (se não existir)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN 
        ALTER TABLE postgrad_students ADD COLUMN foto_matricula TEXT; 
    END IF;

    -- 3. Auditoria para PAYMENTS (Financeiro)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='active') THEN 
        ALTER TABLE payments ADD COLUMN active BOOLEAN DEFAULT TRUE; 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='creator_name') THEN 
        ALTER TABLE payments ADD COLUMN creator_name VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='creator_position') THEN 
        ALTER TABLE payments ADD COLUMN creator_position VARCHAR(255); 
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='creator_photo_url') THEN 
        ALTER TABLE payments ADD COLUMN creator_photo_url TEXT; 
    END IF;

    -- 4. Limpeza de colunas antigas ou conflitantes (Opcional, mas recomendado para sanidade)
    -- Se houver colunas redundantes que causavam erro no hibernate, remova-as aqui.
    -- Exemplo: IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_processo') THEN
    --     ALTER TABLE postgrad_students DROP COLUMN foto_processo;
    -- END IF;

END $$;

-- 5. Verificação de Integridade Final
SELECT 'SINCRO_OK' as status, table_name, column_name 
FROM information_schema.columns 
WHERE table_name IN ('students', 'postgrad_students', 'payments') 
AND column_name IN ('active', 'creator_name', 'foto_matricula');
