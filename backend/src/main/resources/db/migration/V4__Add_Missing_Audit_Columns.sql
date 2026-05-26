-- =============================================================================
-- SCRIPT DE AJUSTE DE AUDITORIA: ADICIONAR COLUNAS FALTANTES
-- Versão: 4.0 (Flyway Migration)
-- Objetivo: Adicionar a coluna last_modified_by nas tabelas que herdam de BaseAuditEntity
-- =============================================================================

BEGIN;

-- Adiciona a coluna last_modified_by na tabela courses
ALTER TABLE courses ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

-- Adiciona a coluna last_modified_by na tabela payments
ALTER TABLE payments ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

-- Adiciona a coluna last_modified_by na tabela staff_members
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

COMMIT;
