-- ==========================================================
-- SCRIPT DE INICIALIZAÇÃO COMPLETO: PORTALCURSOS.NG
-- PROTOCOLO V30.0-SUPREME (DEEP INFRASTRUCTURE)
-- FOCO: NEON POSTGRESQL CLOUD RESILIENCE
-- ==========================================================

-- 1. EXTENSÕES NECESSÁRIAS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. TABELAS DE SEGURANÇA E ACESSO
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(30) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(120) NOT NULL,
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token VARCHAR(255) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45)
);

-- 3. TABELAS ACADÊMICAS (ALUNOS E GRADUAÇÃO)
CREATE TABLE IF NOT EXISTS students (
    id SERIAL PRIMARY KEY,
    registration_number VARCHAR(255) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    cpf VARCHAR(14) NOT NULL,
    phone VARCHAR(20),
    date_of_birth VARCHAR(20),
    address VARCHAR(255),
    current_course VARCHAR(255),
    enrollment_status VARCHAR(20) DEFAULT 'PENDENTE',
    nacionalidade VARCHAR(255),
    estado_civil VARCHAR(255),
    sexo VARCHAR(50),
    numero_reservista VARCHAR(255),
    titulo_eleitor VARCHAR(255),
    is_estrangeiro BOOLEAN DEFAULT FALSE,
    forma_ingresso VARCHAR(50),
    tipo_cota VARCHAR(50),
    user_id BIGINT REFERENCES users(id)
);

-- 4. TABELAS DE PÓS-GRADUAÇÃO
CREATE TABLE IF NOT EXISTS postgrad_students (
    id SERIAL PRIMARY KEY,
    registration_number VARCHAR(20) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    cpf VARCHAR(14) UNIQUE NOT NULL,
    phone VARCHAR(20),
    date_of_birth VARCHAR(20),
    address VARCHAR(255),
    graduation_institution VARCHAR(255) NOT NULL,
    graduation_year INTEGER,
    desired_course VARCHAR(255) NOT NULL,
    enrollment_status VARCHAR(20) DEFAULT 'PENDENTE',
    diploma_file_path VARCHAR(255),
    rg_cpf_file_path VARCHAR(255),
    proof_of_address_file_path VARCHAR(255),
    academic_transcript_file_path VARCHAR(255),
    registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS student_documents (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDENTE',
    file_path VARCHAR(255),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    student_id BIGINT REFERENCES students(id)
);

-- 5. TABELA DE CURSOS (SISTEMA E-MEC 2026)
CREATE TABLE IF NOT EXISTS courses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    codigo_ies VARCHAR(255) NOT NULL,
    denominacao_curso VARCHAR(255) NOT NULL,
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
    code VARCHAR(50)
);

-- 6. FINANCEIRO E MANUTENÇÃO
CREATE TABLE IF NOT EXISTS payments (
    id SERIAL PRIMARY KEY,
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
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS repair_tickets (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    reported_by_id BIGINT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS repair_photos (
    repair_ticket_id BIGINT NOT NULL REFERENCES repair_tickets(id) ON DELETE CASCADE,
    photo_url VARCHAR(255) NOT NULL
);

-- 7. TELEMETRIA V30-SUPREME
CREATE TABLE IF NOT EXISTS system_telemetry (
    id SERIAL PRIMARY KEY,
    check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    component VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms INTEGER,
    details TEXT
);

-- 8. ÍNDICES DE PERFORMANCE (PROTOCOL V30.0-SUPREME)
CREATE INDEX IF NOT EXISTS idx_users_username_active ON users(username) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_sessions_lookup ON user_sessions(user_id, refresh_token);
CREATE INDEX IF NOT EXISTS idx_students_cpf ON students(cpf);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_cpf ON postgrad_students(cpf);
CREATE INDEX IF NOT EXISTS idx_courses_active ON courses(active);
CREATE INDEX IF NOT EXISTS idx_telemetry_recent ON system_telemetry(check_time DESC);
CREATE INDEX IF NOT EXISTS idx_repair_status ON repair_tickets(status);

-- 9. DADOS INICIAIS (SEED)
INSERT INTO roles (name) VALUES ('ROLE_ROOT_MASTER') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_SECRETARIA') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_FINANCEIRO') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_ACADEMICO') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_MATRICULA') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_COORDENADOR') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_PROFESSOR') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_ALUNO') ON CONFLICT (name) DO NOTHING;

-- ANALYZE PARA OPTIMIZER
ANALYZE users;
ANALYZE postgrad_students;
ANALYZE courses;

-- SCRIPT CONCLUÍDO - V30.0-SUPREME
