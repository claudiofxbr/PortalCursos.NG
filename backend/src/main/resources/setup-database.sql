-- ==========================================================
-- SCRIPT DE INICIALIZAÇÃO COMPLETO: PORTALCURSOS.NG
-- PROTOCOLO OMEGA-SUPREME V35.0 (AUTO-CORREÇÃO TOTAL)
-- FOCO: RESILIÊNCIA CLOUD E INTEGRAÇÃO DE DOCUMENTOS PÓS
-- ==========================================================

-- 1. EXTENSÕES NECESSÁRIAS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. TABELAS DE SEGURANÇA E ACESSO (PADRONIZADAS BIGSERIAL)
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(120) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
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

-- 3. TABELAS ACADÊMICAS (ALUNOS E GRADUAÇÃO)
CREATE TABLE IF NOT EXISTS students (
    id BIGSERIAL PRIMARY KEY,
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
    foto_matricula VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT,
    user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_cpf_format CHECK (cpf ~ '^\d{3}\.\d{3}\.\d{3}-\d{2}$' OR cpf ~ '^\d{11}$')
);

-- 4. TABELAS DE PÓS-GRADUAÇÃO (PADRONIZADAS OMEGA)
CREATE TABLE IF NOT EXISTS postgrad_students (
    id BIGSERIAL PRIMARY KEY,
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
    foto_matricula VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_postgrad_cpf CHECK (cpf ~ '^\d{3}\.\d{3}\.\d{3}-\d{2}$' OR cpf ~ '^\d{11}$')
);

CREATE TABLE IF NOT EXISTS student_documents (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDENTE',
    file_path VARCHAR(255),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    student_id BIGINT REFERENCES students(id)
);

-- 5. TABELA DE CURSOS (GESTÃO DO MEC - UUID MANTIDO PARA CURSOS)
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
    code VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT
);

-- 6. FINANCEIRO E MANUTENÇÃO (PADRONIZADAS)
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6.1 CONTROLE INSTITUCIONAL (PADRONIZADA)
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repair_tickets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN',
    main_photo_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    reported_by_id BIGINT REFERENCES users(id),
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT
);

CREATE TABLE IF NOT EXISTS repair_photos (
    id BIGSERIAL PRIMARY KEY,
    repair_ticket_id BIGINT NOT NULL REFERENCES repair_tickets(id) ON DELETE CASCADE,
    photo_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. TELEMETRIA E LOGS OMEGA-SUPREME
CREATE TABLE IF NOT EXISTS system_telemetry (
    id BIGSERIAL PRIMARY KEY,
    check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    component VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms INTEGER,
    details TEXT
);

CREATE TABLE IF NOT EXISTS deployment_logs (
    id BIGSERIAL PRIMARY KEY,
    deploy_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    summary TEXT
);

-- 8. ÍNDICES DE PERFORMANCE (PROTOCOL OMEGA-SUPREME)
CREATE INDEX IF NOT EXISTS idx_users_username_active ON users(username) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_sessions_lookup ON user_sessions(user_id, refresh_token);
CREATE INDEX IF NOT EXISTS idx_students_cpf ON students(cpf);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_cpf ON postgrad_students(cpf);
CREATE INDEX IF NOT EXISTS idx_courses_active ON courses(active);
CREATE INDEX IF NOT EXISTS idx_telemetry_recent ON system_telemetry(check_time DESC);
CREATE INDEX IF NOT EXISTS idx_repair_status ON repair_tickets(status);
CREATE INDEX IF NOT EXISTS idx_payments_status_due ON payments(status, due_date);

-- 9. DADOS INICIAIS (SEED)
INSERT INTO roles (id, name) VALUES (1, 'ROLE_ROOT_MASTER') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (2, 'ROLE_ADMIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (3, 'ROLE_SECRETARIA') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (4, 'ROLE_FINANCEIRO') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (5, 'ROLE_ACADEMICO') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (6, 'ROLE_MATRICULA') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (7, 'ROLE_COORDENADOR') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (8, 'ROLE_PROFESSOR') ON CONFLICT (id) DO NOTHING;
INSERT INTO roles (id, name) VALUES (9, 'ROLE_ALUNO') ON CONFLICT (id) DO NOTHING;

-- 10. USUÁRIO ADMINISTRADOR PADRÃO (admin / admin123)
-- SENHA BCRYPT: admin123
INSERT INTO users (username, email, password, active) 
SELECT 'admin', 'admin@portalcursos.com.br', '$2a$10$8.UnVuG9UMJSuGPvstLmeuQC9s.nx.zGDzE7zC.4L8Cis0HAnqZ7G', true
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- VINCULAR ADMIN ÀS ROLES NECESSÁRIAS
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'admin' AND r.name IN ('ROLE_ADMIN', 'ROLE_ROOT_MASTER')
ON CONFLICT DO NOTHING;

-- VINCULAR STAFFMEMBER AO ADMIN (PARA AUDITORIA OMEGA)
INSERT INTO staff_members (full_name, position, department, user_id)
SELECT 'Administrador Supremo', 'ROOT', 'TI-INFRA', u.id FROM users u 
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;

-- ATUALIZAÇÃO DE SEQUÊNCIAS (SE NECESSÁRIO)
SELECT setval('roles_id_seq', (SELECT MAX(id) FROM roles));
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- ANALYZE PARA OPTIMIZER
ANALYZE users;
ANALYZE roles;
ANALYZE user_roles;
ANALYZE postgrad_students;
ANALYZE courses;
ANALYZE payments;

-- REGISTRO DE INICIALIZAÇÃO OMEGA V35.0
INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V35.0-ULTRA-SUPREME', 'SUCCESS', 'HYBRID-CLOUD', 'Protocolo de Auto-Correção de Schema e Estabilização de Pós-Graduação concluídos.');

-- SCRIPT CONCLUÍDO - V35.0-ULTRA-SUPREME
