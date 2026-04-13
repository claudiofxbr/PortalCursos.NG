-- =============================================
-- PROTOCOLO V37.2-ULTRA-COURSE-FIX (MEC)
-- OBJETIVO: Sincronização de Auditoria e Fotos para GESTÃO DE CURSOS
-- ALVO: Neon PostgreSQL (PortalCursos.NG)
-- =============================================

DO $$ 
BEGIN 
    -- 1. Auditoria e Rastreabilidade para COURSES (Gestão do MEC)
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
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='registration_date') THEN 
        ALTER TABLE courses ADD COLUMN registration_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP; 
    END IF;

    -- 2. Garantir que registros legados sejam marcados como ativos
    UPDATE courses SET active = TRUE WHERE active IS NULL;

END $$;

-- 3. Verificação de Integridade
SELECT 'COURSE_SINCRO_OK' as status, table_name, column_name 
FROM information_schema.columns 
WHERE table_name = 'courses' 
AND column_name IN ('active', 'creator_name', 'creator_photo_url', 'registration_date');
