-- ==========================================================
-- V14: Consolida modelo de aluno de pós-graduação e adiciona
-- constraints de integridade faltantes.
--
-- Contexto (auditoria de banco de dados):
-- 1. PostgradStudent é uma subclasse de Student via single-table
--    inheritance (tabela `students`, discriminador `student_type`),
--    mas a FK `payments.postgrad_student_id` ainda apontava para a
--    tabela legada `postgrad_students`, cujos IDs são uma sequência
--    independente da de `students.id` — os dois nunca deveriam ter
--    coexistido. Tabelas confirmadas vazias em produção (sem dados
--    a migrar) antes desta migration.
-- 2. `students.cpf`/`students.email` não tinham UNIQUE real,
--    dependendo só de checagem em código antes do INSERT.
-- 3. `courses.curso_graduacao_vinculado_id` não tinha FK.
-- 4. Faltava índice composto para o filtro financeiro mais comum.
-- ==========================================================

-- 1. Remove a FK/coluna legada de payments (unificado em student_id,
--    que já referencia `students`, tabela que cobre graduação e pós).
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_postgrad_student_id_fkey;
ALTER TABLE payments DROP COLUMN IF EXISTS postgrad_student_id;

-- 2. Remove a tabela legada duplicada (vazia, substituída pela
--    hierarquia single-table em `students`).
DROP TABLE IF EXISTS postgrad_students CASCADE;

-- 3. Unicidade real de CPF/e-mail de aluno.
ALTER TABLE students ADD CONSTRAINT uq_students_cpf UNIQUE (cpf);
ALTER TABLE students ADD CONSTRAINT uq_students_email UNIQUE (email);

-- 4. Integridade referencial do vínculo curso de pós -> curso de graduação.
ALTER TABLE courses ADD CONSTRAINT fk_courses_curso_graduacao_vinculado
    FOREIGN KEY (curso_graduacao_vinculado_id) REFERENCES courses(id) ON DELETE SET NULL;

-- 5. Índice composto para o filtro financeiro mais comum (nível + categoria + status).
CREATE INDEX IF NOT EXISTS idx_payments_level_category_status
    ON payments(academic_level, category, status);
