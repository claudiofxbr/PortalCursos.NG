-- V6__Unified_Students_Inheritance.sql
-- Adiciona coluna discriminadora student_type e colunas de pós-graduação na tabela students

ALTER TABLE students ADD COLUMN IF NOT EXISTS student_type          VARCHAR(50)  DEFAULT 'UNDERGRAD';
ALTER TABLE students ADD COLUMN IF NOT EXISTS graduation_institution VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS graduation_year        INTEGER;
ALTER TABLE students ADD COLUMN IF NOT EXISTS desired_course         VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_students_type ON students(student_type);
