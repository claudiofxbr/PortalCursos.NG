-- V1__Initial_Schema.sql
-- Consolidação e Estabilização do Esquema PortalCursos.NG

-- 1. Tabela de Roles (Perfis)
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

-- REMOVER CONSTRAINT PROBLEMATICA se existir
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.constraint_column_usage WHERE table_name = 'roles' AND constraint_name = 'roles_name_check') THEN
        ALTER TABLE roles DROP CONSTRAINT roles_name_check;
    END IF;
END $$;

-- 2. Tabela de Usuários
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    foto_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tabela de Relacionamento Usuário-Role
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token VARCHAR(255) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Garantir Perfis Institucionais
INSERT INTO roles (name) VALUES 
('ROLE_ROOT_MASTER'),
('ROLE_ADMIN'),
('ROLE_SECRETARIA'),
('ROLE_FINANCEIRO'),
('ROLE_ACADEMICO'),
('ROLE_MATRICULA'),
('ROLE_COORDENADOR'),
('ROLE_PROFESSOR'),
('ROLE_MONITOR'),
('ROLE_BIBLIOTECARIO'),
('ROLE_ALUNO'),
('ROLE_CANDIDATO')
ON CONFLICT (name) DO NOTHING;

-- 5. Tabelas de Negócio (Garantir colunas de auditoria)
-- Alunos (Graduação)
DO $$ BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'students') THEN
        CREATE TABLE students (id BIGSERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL);
    END IF;
END $$;

ALTER TABLE students ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE students ADD COLUMN IF NOT EXISTS creator_name VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS creator_photo_url TEXT;
ALTER TABLE students ADD COLUMN IF NOT EXISTS foto_matricula VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Alunos (Pós-Graduação)
DO $$ BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'postgrad_students') THEN
        CREATE TABLE postgrad_students (id BIGSERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL);
    END IF;
END $$;

ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS creator_name VARCHAR(255);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS foto_matricula VARCHAR(255);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS graduation_institution VARCHAR(255);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS academic_transcript_file_path VARCHAR(255);
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 6. Campus Care (Reparos)
CREATE TABLE IF NOT EXISTS repair_tickets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    location VARCHAR(255),
    status VARCHAR(50) DEFAULT 'OPEN',
    main_photo_url TEXT,
    reported_by_name VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repair_photos (
    repair_ticket_id BIGINT NOT NULL,
    photo_url TEXT,
    CONSTRAINT fk_repair_photos_ticket FOREIGN KEY (repair_ticket_id) REFERENCES repair_tickets(id) ON DELETE CASCADE
);

-- 7. Criação inicial de tabelas em falta (courses e staff_members) para compatibilidade com as migrações V3-V7
CREATE TABLE IF NOT EXISTS staff_members (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    position VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    foto_url VARCHAR(255),
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo_ies VARCHAR(255),
    denominacao_curso VARCHAR(255),
    nivel_pos_graduacao VARCHAR(30),
    modalidade VARCHAR(20),
    area_conhecimento VARCHAR(255),
    carga_horaria_total INTEGER,
    curso_graduacao_vinculado_id UUID,
    numero_documento_criacao VARCHAR(255),
    data_documento_criacao DATE,
    data_inicio_oferta DATE,
    cpf_coordenador VARCHAR(14),
    titulacao_coordenador VARCHAR(255),
    percentual_docentes_stricto_sensu DOUBLE PRECISION,
    is_locked BOOLEAN DEFAULT FALSE,
    name VARCHAR(255),
    description TEXT,
    type VARCHAR(20),
    duration_in_semesters INTEGER,
    total_vacancies INTEGER,
    coordinator_name VARCHAR(255),
    monthly_fee DOUBLE PRECISION,
    active BOOLEAN DEFAULT TRUE,
    code VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    amount DECIMAL(10,2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    method VARCHAR(20),
    payment_code VARCHAR(255),
    student_id BIGINT REFERENCES students(id),
    postgrad_student_id BIGINT REFERENCES postgrad_students(id),
    academic_level VARCHAR(20),
    category VARCHAR(20),
    secretary_process_type VARCHAR(20),
    description VARCHAR(255),
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT,
    student_photo_url TEXT,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS teachers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    specialization VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL
);
