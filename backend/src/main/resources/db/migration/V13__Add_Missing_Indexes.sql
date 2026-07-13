-- V13__Add_Missing_Indexes.sql
-- Índices ausentes identificados em auditoria de performance (jul/2026):
-- payments e students.email/cpf são consultados com frequência (telas
-- financeiras e checagem de duplicidade no cadastro) e não tinham índice,
-- forçando full table scan à medida que essas tabelas crescem.

CREATE INDEX IF NOT EXISTS idx_payments_student_id          ON payments(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_postgrad_student_id ON payments(postgrad_student_id);
CREATE INDEX IF NOT EXISTS idx_payments_status              ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_academic_level_status ON payments(academic_level, status);
CREATE INDEX IF NOT EXISTS idx_payments_due_date_status      ON payments(due_date, status);

CREATE INDEX IF NOT EXISTS idx_students_email ON students(email);
CREATE INDEX IF NOT EXISTS idx_students_cpf   ON students(cpf);
