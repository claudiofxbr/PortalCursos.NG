-- PortalCursos.NG - Script de Otimização Supremo V30.9
-- Foco: Performance, Integridade e Robustez nas Matrículas

-- 1. Índices para Busca de Alunos (Graduação e Pós)
CREATE INDEX IF NOT EXISTS idx_student_cpf ON students(cpf);
CREATE INDEX IF NOT EXISTS idx_student_email ON students(email);
CREATE INDEX IF NOT EXISTS idx_student_active ON students(active);

CREATE INDEX IF NOT EXISTS idx_postgrad_cpf ON postgrad_students(cpf);
CREATE INDEX IF NOT EXISTS idx_postgrad_email ON postgrad_students(email);

-- 2. índices para Auditoria (Staff e Usuários)
CREATE INDEX IF NOT EXISTS idx_staff_user_id ON staff_members(user_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- 3. índices para Controle Financeiro (Payments)
CREATE INDEX IF NOT EXISTS idx_payments_student_id ON payments(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_due_date ON payments(due_date);

-- 4. Garantia de Unicidade em campos críticos (Safe-Guard)
-- Nota: Rodar estas alterações apenas se as constraints ainda não existirem.
-- ALTER TABLE students ADD CONSTRAINT uk_student_cpf UNIQUE (cpf);
-- ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);

SELECT 'PROTOCOLO SUPREME: Otimização de Banco de Dados Concluída' as status;
