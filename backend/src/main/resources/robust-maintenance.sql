-- ==========================================================
-- SCRIPT DE MANUTENÇÃO ROBUSTA: PORTALCURSOS.NG
-- PROTOCOLO V30.9-CLEANUP
-- OBJETIVO: OTIMIZAÇÃO DE SESSÕES E DOCUMENTOS
-- ==========================================================

-- 1. LIMPEZA DE SESSÕES EXPIRADAS (MANUTENÇÃO PREVENTIVA)
DELETE FROM user_sessions WHERE expiry_date < CURRENT_TIMESTAMP;

-- 2. ÍNDICES PARA PERFORMANCE DE LOGIN E SESSÃO ÚNICA
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_refresh_token ON user_sessions(refresh_token);

-- 3. ÍNDICES PARA MÓDULO ACADÊMICO (GRADUAÇÃO)
CREATE INDEX IF NOT EXISTS idx_student_documents_student_id ON student_documents(student_id);
CREATE INDEX IF NOT EXISTS idx_student_documents_status ON student_documents(status);

-- 4. GARANTIR INTEGRIDADE DE AUDITORIA (PROTOCOLO SUPREME)
-- Adiciona colunas se não existirem (redundância de segurança)
ALTER TABLE students ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS created_by_position VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS updated_by_position VARCHAR(255);

-- 5. REGISTRO DE MANUTENÇÃO
INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V30.9-ROBUST', 'SUCCESS', 'DATABASE', 'Limpeza de sessões e criação de índices concluída.');
