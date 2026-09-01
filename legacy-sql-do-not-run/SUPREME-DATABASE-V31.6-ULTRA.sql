-- SCRIPT SQL V31.6-ULTRA (CORREÇÃO DEFINITIVA)
-- Resolve: ERROR: column "foto_matricula" of relation "postgrad_students" already exists

DO $$ 
BEGIN 
    -- 1. Auditoria para Students (Graduação)
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

    -- 2. Auditoria para Postgrad Students (Pós-Graduação)
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

    -- 3. Padronização de Foto (Renomeação Robusta - Graduação)
    -- Só renomeia se 'foto_url' existir E 'foto_matricula' NÃO existir
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_url') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students RENAME COLUMN foto_url TO foto_matricula;
    END IF;
    -- Se ambos existirem, deleta a antiga apenas
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_url') 
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='foto_matricula') THEN
        ALTER TABLE students DROP COLUMN foto_url;
    END IF;

    -- 4. Padronização de Foto (Renomeação Robusta - Pós-Graduação)
    -- Só renomeia se 'foto_url' existir E 'foto_matricula' NÃO existir
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_url') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN
        ALTER TABLE postgrad_students RENAME COLUMN foto_url TO foto_matricula;
    END IF;
    -- Se ambos existirem, deleta a antiga apenas
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_url') 
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='foto_matricula') THEN
        ALTER TABLE postgrad_students DROP COLUMN foto_url;
    END IF;

END $$;
