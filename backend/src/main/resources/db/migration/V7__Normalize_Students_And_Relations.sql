-- =============================================================================
-- SCRIPT DE NORMALIZAÇÃO DE ESTUDANTES E RELAÇÕES (V7) - PORTALCURSOS.NG
-- Versão: 7.0 (Flyway Migration)
-- Objetivo: Garantir a compatibilidade do banco com a classe Student.java do JPA
-- =============================================================================

BEGIN;

-- 1. Tratar a coluna name -> full_name de forma segura
DO $$
BEGIN
    -- Se existia uma coluna 'name' antiga, mas não existia 'full_name', renomeia
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='name') 
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='full_name') THEN
        ALTER TABLE students RENAME COLUMN name TO full_name;
    -- Se não existia nenhuma das duas, cria 'full_name'
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='full_name') THEN
        ALTER TABLE students ADD COLUMN full_name VARCHAR(255) NOT NULL DEFAULT 'Estudante Sem Nome';
        ALTER TABLE students ALTER COLUMN full_name DROP DEFAULT;
    END IF;
END $$;

-- 2. Adicionar as colunas em falta na tabela students usando blocos seguros e IF NOT EXISTS
ALTER TABLE students ADD COLUMN IF NOT EXISTS registration_number VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS cpf VARCHAR(14);
ALTER TABLE students ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE students ADD COLUMN IF NOT EXISTS date_of_birth VARCHAR(20);
ALTER TABLE students ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS current_course VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS enrollment_status VARCHAR(50) DEFAULT 'PENDENTE';
ALTER TABLE students ADD COLUMN IF NOT EXISTS nacionalidade VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS sexo VARCHAR(50);
ALTER TABLE students ADD COLUMN IF NOT EXISTS numero_reservista VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS titulo_eleitor VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS is_estrangeiro BOOLEAN DEFAULT FALSE;
ALTER TABLE students ADD COLUMN IF NOT EXISTS forma_ingresso VARCHAR(50);
ALTER TABLE students ADD COLUMN IF NOT EXISTS tipo_cota VARCHAR(50);

-- 3. Adicionar as chaves estrangeiras de forma segura (course_id e user_id)
DO $$
BEGIN
    -- Adicionar coluna course_id se não existir
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='course_id') THEN
        ALTER TABLE students ADD COLUMN course_id UUID REFERENCES courses(id) ON DELETE SET NULL;
    END IF;

    -- Adicionar coluna user_id se não existir
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='user_id') THEN
        ALTER TABLE students ADD COLUMN user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 4. Adicionar restrição UNIQUE para registration_number se não existir
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE table_name='students' AND constraint_name='students_registration_number_key') THEN
        ALTER TABLE students ADD CONSTRAINT students_registration_number_key UNIQUE (registration_number);
    END IF;
END $$;

-- 5. Garantir que a tabela student_documents existe de forma totalmente compatível com o JPA
CREATE TABLE IF NOT EXISTS student_documents (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING',
    file_path VARCHAR(255),
    rejection_reason VARCHAR(255),
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    student_id BIGINT REFERENCES students(id) ON DELETE CASCADE
);

-- Se a tabela student_documents já existia com a modelagem antiga, garantir que tenha as novas colunas
ALTER TABLE student_documents ADD COLUMN IF NOT EXISTS document_type VARCHAR(100);
ALTER TABLE student_documents ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(255);
ALTER TABLE student_documents ADD COLUMN IF NOT EXISTS upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 6. Criar índices para otimização de performance
CREATE INDEX IF NOT EXISTS idx_students_course_id ON students(course_id);
CREATE INDEX IF NOT EXISTS idx_students_user_id ON students(user_id);
CREATE INDEX IF NOT EXISTS idx_student_documents_student_id ON student_documents(student_id);

COMMIT;
