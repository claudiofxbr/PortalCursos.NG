-- ==========================================================
-- SCRIPT DE CURA INSTITUCIONAL: PORTALCURSOS.NG
-- PROTOCOLO COLLAB-SUPREME V37.1 (ULTRA-PERFORMANCE)
-- OBJETIVO: GARANTIR REGISTROS ÓRFÃOS E ÍNDICES DE VELOCIDADE
-- ==========================================================

-- 0. AUTO-REPARO DE TABELAS
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
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1. ADICIONAR ÍNDICE DE PERFORMANCE (EVITA TRAVAMENTOS EM QUERIES DE BUSCA)
-- O erro de "travamento" muitas vezes ocorre por falta de índice em tabelas que crescem.
CREATE UNIQUE INDEX IF NOT EXISTS idx_staff_user_id ON staff_members(user_id);

-- 2. IDENTIFICAR USUÁRIOS INSTITUCIONAIS SEM REGISTRO STAFF E CRIAR
-- Usa o username em caixa alta como Nome Completo padrão na falta de um.
INSERT INTO staff_members (full_name, position, department, user_id, foto_url, active)
SELECT 
    UPPER(u.username) as full_name,
    'CARGO_REVISAR' as position,
    'GERAL_INSTITUCIONAL' as department,
    u.id as user_id,
    u.foto_url,
    true
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE r.name NOT IN ('ROLE_ALUNO', 'ROLE_CANDIDATO')
AND u.active = true
AND NOT EXISTS (SELECT 1 FROM staff_members sm WHERE sm.user_id = u.id);

-- 3. GARANTIR QUE O USUÁRIO 'admin' SEJA ROOT NA STAFF SE NÃO FOR
UPDATE staff_members 
SET position = 'ROOT_MASTER', department = 'TI-ADMIN'
WHERE user_id = (SELECT id FROM users WHERE username = 'admin');

-- 4. LOG DE EXECUÇÃO
CREATE TABLE IF NOT EXISTS deployment_logs (
    id BIGSERIAL PRIMARY KEY,
    deploy_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    summary TEXT
);

INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V37.1-ULTRA', 'SUCCESS', 'HYBRID-CLOUD', 'Protocolo COLLAB V37.1: Índices de performance e auto-reparo aplicados para evitar travamentos.');

-- SCRIPT CONCLUÍDO - V37.1-ULTRA-PERFORMANCE
