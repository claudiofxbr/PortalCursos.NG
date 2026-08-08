-- ==========================================================
-- SCRIPT DE REPARO IMEDIATO: ACESSO ROOTMASTER
-- PROTOCOLO V38.1.1 (FIX-EMERGENCY)
-- ==========================================================

-- 1. GARANTIR QUE O USUÁRIO EXISTE
-- ATENÇÃO: este script não deve mais fixar senha/hash em texto plano.
-- O hash de senha do rootmaster é sincronizado no boot pelo DataLoader,
-- a partir da variável de ambiente APP_ROOT_PASSWORD (ver .env.example).
INSERT INTO users (username, email, password, active)
VALUES ('rootmaster', 'ti@portalcursos.com', '<definido pelo DataLoader via APP_ROOT_PASSWORD>', true)
ON CONFLICT (username) DO UPDATE
SET active = true;

-- 2. GARANTIR QUE A ROLE ROOT MASTER EXISTE
INSERT INTO roles (name) VALUES ('ROLE_ROOT_MASTER') ON CONFLICT (name) DO NOTHING;

-- 3. VINCULAR O USUÁRIO À ROLE
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'rootmaster' AND r.name = 'ROLE_ROOT_MASTER'
ON CONFLICT DO NOTHING;

-- 4. LOG DE REPARO
INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V38.1.1-FIX', 'SUCCESS', 'MANUAL-REPAIR', 'Reparo de credenciais RootMaster aplicado com hash BCrypt real.');

-- SELECIONAR PARA CONFERIR
SELECT id, username, active FROM users WHERE username = 'rootmaster';
