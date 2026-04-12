-- ==========================================================
-- SCRIPT DE REPARO IMEDIATO: ACESSO ROOTMASTER
-- PROTOCOLO V38.1.1 (FIX-EMERGENCY)
-- ==========================================================

-- 1. GARANTIR QUE O USUÁRIO EXISTE E TEM A SENHA CORRETA
-- Senha: qzWX312#!@
INSERT INTO users (username, email, password, active) 
VALUES ('rootmaster', 'ti@portalcursos.com', '$2b$12$52.S5lgBkOdRSBkGHRByIu43Lxq7c13FTjuwhE7dZemPDs.ck73D.', true)
ON CONFLICT (username) DO UPDATE 
SET password = EXCLUDED.password, active = true;

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
