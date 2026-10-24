-- ==========================================================
-- SCRIPT DE MIGRAÇÃO: PORTALCURSOS.NG
-- PROTOCOLO V38.4 - EXPANSÃO DE CAPACIDADE DE USUÁRIOS
-- OBJETIVO: AUMENTAR LIMITES DE USERNAME E EMAIL NO BANCO DE DADOS
-- ==========================================================

-- 1. AUMENTAR LIMITES NA TABELA USERS
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(100);
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(100);

-- 2. REGISTRO DE LOG
INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V38.4-LIMITS', 'SUCCESS', 'HYBRID-CLOUD', 'Protocolo V38.4: Limites de username e email expandidos para 100 caracteres.');

-- 3. ANALYZE PARA ATUALIZAR ESTATÍSTICAS
ANALYZE users;

-- SCRIPT CONCLUÍDO - V38.4
