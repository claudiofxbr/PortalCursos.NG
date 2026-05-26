-- =============================================================================
-- SCRIPT DE UNIFICAÇÃO DE HERANÇA DE ESTUDANTES (V6) - PORTALCURSOS.NG
-- Versão: 6.0 (Flyway Migration)
-- Objetivo: Adicionar coluna de discriminação student_type e colunas específicas de PostgradStudent na tabela students para habilitar SINGLE_TABLE inheritance
-- =============================================================================

BEGIN;

-- 1. Adicionar coluna do discriminador student_type
ALTER TABLE students ADD COLUMN IF NOT EXISTS student_type VARCHAR(50) DEFAULT 'UNDERGRAD';

-- 2. Adicionar as colunas de pós-graduação caso não existam
ALTER TABLE students ADD COLUMN IF NOT EXISTS graduation_institution VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS graduation_year INTEGER;
ALTER TABLE students ADD COLUMN IF NOT EXISTS desired_course VARCHAR(255);

-- 3. Criar índice para o tipo de estudante para otimizar queries polimórficas
CREATE INDEX IF NOT EXISTS idx_students_type ON students(student_type);

COMMIT;
