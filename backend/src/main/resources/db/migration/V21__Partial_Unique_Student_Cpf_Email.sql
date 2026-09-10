-- Auditoria 3.3: as constraints UNIQUE totais de students(cpf) e students(email)
-- impediam o re-cadastro de um aluno apos soft-delete (active = false), pois o
-- registro inativo continuava ocupando o valor. Trocamos por indices UNIQUE
-- parciais que so consideram registros ativos, permitindo reaproveitar cpf/email
-- de alunos desativados.

ALTER TABLE students DROP CONSTRAINT IF EXISTS uq_students_cpf;
ALTER TABLE students DROP CONSTRAINT IF EXISTS uq_students_email;

CREATE UNIQUE INDEX IF NOT EXISTS uq_students_cpf_active ON students(cpf) WHERE active = true;
CREATE UNIQUE INDEX IF NOT EXISTS uq_students_email_active ON students(email) WHERE active = true;
