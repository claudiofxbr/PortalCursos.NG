-- ==========================================================
-- SCRIPT DE CURA INSTITUCIONAL: PORTALCURSOS.NG
-- PROTOCOLO COLLAB-SUPREME V37.2 (ULTRA-RESILIENTE)
-- OBJETIVO: GARANTIR REGISTROS ÓRFÃOS, ÍNDICES E PERFORMANCE
-- ==========================================================

-- 0. AUTO-REPARO DE TABELAS
CREATE TABLE IF NOT EXISTS staff_members (
    id BIGINT PRIMARY KEY, -- MUDANÇA: Usará o mesmo ID do User via @MapsId no Backend
    full_name VARCHAR(255) NOT NULL,
    position VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    foto_url VARCHAR(255),
    creator_name VARCHAR(255),
    creator_position VARCHAR(255),
    creator_photo_url TEXT,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE, -- Vínculo forte e único
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1. ADICIONAR ÍNDICES DE PERFORMANCE (EVITA TRAVAMENTOS)
CREATE INDEX IF NOT EXISTS idx_staff_full_name ON staff_members(full_name);
CREATE INDEX IF NOT EXISTS idx_staff_active ON staff_members(active);

-- 2. LIMPEZA DE REGISTROSÓRFÃOS (EVITA DUPLICIDADE QUE CAUSA TRAVAMENTO)
DELETE FROM staff_members 
WHERE user_id IS NULL OR NOT EXISTS (SELECT 1 FROM users WHERE users.id = staff_members.user_id);

-- 3. SINCRONIZAÇÃO EM MASSA (PROTOCOLAR)
INSERT INTO staff_members (id, full_name, position, department, user_id, foto_url, active)
SELECT 
    u.id as id,
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
AND NOT EXISTS (SELECT 1 FROM staff_members sm WHERE sm.user_id = u.id)
ON CONFLICT (id) DO NOTHING;

-- 4. LOG DE EXECUÇÃO SUPREME
CREATE TABLE IF NOT EXISTS deployment_logs (
    id BIGSERIAL PRIMARY KEY,
    deploy_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    summary TEXT
);

INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V37.2-ULTRA', 'SUCCESS', 'HYBRID-CLOUD', 'Protocolo COLLAB V37.2: Refatoração de IDs e Sincronização Robusta aplicada.');

-- SCRIPT CONCLUÍDO - V37.2-ULTRA-RESILIENTE
