-- SCRIPT DE OTIMIZAÇÃO: PORTALCURSOS.NG - V22-ULTRA (Deep Fix Edition)
-- FOCO: BOOT NÃO-BLOQUEANTE, TIMEOUTS GLOBAIS E HEARTBEAT
-- SET statement_timeout = 30000; -- Comentado para resiliência no startup via pooler

-- 1. Otimização de Autenticação (Acesso Imediato)
CREATE INDEX IF NOT EXISTS idx_users_username_active ON users(username) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);
CREATE INDEX IF NOT EXISTS idx_user_roles_composite ON user_roles(user_id, role_id);

-- 2. Otimização de Sessões (Prevenção de travas de Cold Start)
CREATE INDEX IF NOT EXISTS idx_user_sessions_lookup ON user_sessions(user_id, last_activity);
CREATE INDEX IF NOT EXISTS idx_user_sessions_token ON user_sessions(refresh_token);

-- 3. Módulo Acadêmico e Pós-Graduação (Busca Multidimensional)
CREATE INDEX IF NOT EXISTS idx_students_cpf_lookup ON students(cpf);
CREATE INDEX IF NOT EXISTS idx_pg_students_composite ON pg_students(student_id, enrollment_status);
CREATE INDEX IF NOT EXISTS idx_pgs_documents_audit ON pgs_documents(student_id, uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_cpf ON postgrad_students(cpf);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_email ON postgrad_students(email);
CREATE INDEX IF NOT EXISTS idx_postgrad_students_status ON postgrad_students(enrollment_status);
CREATE INDEX IF NOT EXISTS idx_postgrad_registration ON postgrad_students(registration_number);

-- 4. Cursos e e-MEC
CREATE INDEX IF NOT EXISTS idx_courses_code_active ON courses(code, active);

-- 5. Manutenção de Integridade (Protocolo de Desvincular Latência)
-- REINDEX TABLE users;
-- REINDEX TABLE user_roles;
-- REINDEX TABLE user_sessions;

-- 6. Atualização de Estatísticas para o Query Planner do Neon
ANALYZE users;
ANALYZE roles;
ANALYZE students;
ANALYZE pg_students;
ANALYZE courses;
ANALYZE user_sessions;
ANALYZE postgrad_students;

-- 7. Limpeza e Compactação de Performance (Práticas Neon)
-- VACUUM ANALYZE users;
-- VACUUM ANALYZE postgrad_students;

-- SCRIPT CONCLUÍDO - PROTOCOLO V22-ULTRA (NEON POSTGRESQL)
