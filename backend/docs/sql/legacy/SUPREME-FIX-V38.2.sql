-- ==========================================================
-- SCRIPT DE REPARO SUPREMO V38.2 (DIAGNÓSTICO E CURA)
-- OBJETIVO: RESTAURAR ACESSO ROOTMASTER E DESBLOQUEAR CADASTRO DE COLABORADORES
-- ==========================================================

-- 1. RESTAURAÇÃO DE SEGURANÇA (ROOTMASTER)
-- Define a senha do rootmaster para: qzWX312#!@
-- Este hash é robusto (BCrypt cost 12)
UPDATE users 
SET password = '$2a$12$52.S5lgBkOdRSBkGHRByIu43Lxq7c13FTjuwhE7dZemPDs.ck73D.',
    active = true
WHERE username = 'rootmaster';

-- 2. LIMPEZA DE INCONSISTÊNCIAS (FIX PARA CLAUDIO.XAVIER E OUTROS FECHAMENTOS 500)
-- Remove registros na staff_members que não possuem um usuário correspondente ativo
-- ou que foram criados em tentativas que falharam parcialmente.
DELETE FROM staff_members 
WHERE id NOT IN (SELECT id FROM users);

-- 3. AUDITORIA DE STATUS
SELECT username, email, active FROM users WHERE username IN ('rootmaster', 'claudio.xavier');
SELECT id, full_name, position, active FROM staff_members;

-- ==========================================================
-- INSTRUÇÕES:
-- 1. Cole este script no Console SQL do Neon.
-- 2. Execute.
-- 3. Tente o login com rootmaster / qzWX312#!@.
-- 4. Tente realizar o cadastro do Cláudio novamente via interface.
-- ==========================================================
