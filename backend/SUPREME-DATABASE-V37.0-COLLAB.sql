-- ==========================================================
-- SCRIPT DE CURA INSTITUCIONAL: PORTALCURSOS.NG
-- PROTOCOLO COLLAB-SUPREME V37.0 (AUTO-REPARO E SINCRONIZAÇÃO)
-- OBJETIVO: GARANTIR QUE TODO USUÁRIO INSTITUCIONAL TENHA REGISTRO STAFF
-- ==========================================================

-- 0. GARANTIR QUE A TABELA STAFF_MEMBERS EXISTA (AUTO-REPARO)
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

-- 1. IDENTIFICAR USUÁRIOS INSTITUCIONAIS SEM REGISTRO STAFF E CRIAR
-- Usa o username em caixa alta como Nome Completo padrão na falta de um.
INSERT INTO staff_members (full_name, position, department, user_id, foto_url)
SELECT 
    UPPER(u.username) as full_name,
    'CARGO_REVISAR' as position,
    'GERAL_INSTITUCIONAL' as department,
    u.id as user_id,
    u.foto_url
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE r.name NOT IN ('ROLE_ALUNO', 'ROLE_CANDIDATO')
AND u.active = true
AND NOT EXISTS (SELECT 1 FROM staff_members sm WHERE sm.user_id = u.id);

-- 2. GARANTIR QUE O USUÁRIO 'admin' SEJA ROOT NA STAFF SE NÃO FOR
UPDATE staff_members 
SET position = 'ROOT_MASTER', department = 'TI-ADMIN'
WHERE user_id = (SELECT id FROM users WHERE username = 'admin');

-- 3. LOG DE EXECUÇÃO
-- Garantir que a tabela deployment_logs também exista se o banco for novo
CREATE TABLE IF NOT EXISTS deployment_logs (
    id BIGSERIAL PRIMARY KEY,
    deploy_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    summary TEXT
);

INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V37.0-COLLAB', 'SUCCESS', 'HYBRID-CLOUD', 'Protocolo COLLAB: Sincronização e criação de tabelas institucionais concluída com auto-reparo.');

-- SCRIPT CONCLUÍDO - V37.0-COLLAB
