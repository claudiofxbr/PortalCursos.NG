-- ==========================================================
-- SCRIPT DE NORMALIZAÇÃO OMEGA: PORTALCURSOS.NG
-- V40.0 - UNIFICAÇÃO DE ESTUDANTES E OTIMIZAÇÃO DE SCHEMA
-- ==========================================================

-- 1. UNIFICAÇÃO DA ESTRUTURA DE ESTUDANTES
-- Adicionando colunas de pós-graduação à tabela de estudantes para permitir Single Table Inheritance
ALTER TABLE students ADD COLUMN IF NOT EXISTS student_type VARCHAR(20) DEFAULT 'GRADUATION';
ALTER TABLE students ADD COLUMN IF NOT EXISTS graduation_institution VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS graduation_year INTEGER;
ALTER TABLE students ADD COLUMN IF NOT EXISTS desired_course VARCHAR(255);

-- 2. MIGRAÇÃO DE DADOS (Caso existam dados em postgrad_students)
-- Esta parte deve ser executada com cautela
INSERT INTO students (
    registration_number, full_name, email, cpf, phone, date_of_birth, address, 
    enrollment_status, foto_matricula, active, created_at, updated_at, 
    student_type, graduation_institution, graduation_year, desired_course
)
SELECT 
    registration_number, full_name, email, cpf, phone, date_of_birth, address, 
    enrollment_status, foto_matricula, active, created_at, updated_at, 
    'POSTGRAD', graduation_institution, graduation_year, desired_course
FROM postgrad_students
ON CONFLICT (cpf) DO NOTHING;

-- 3. MIGRAÇÃO DE DOCUMENTOS DE PÓS PARA A TABELA GENÉRICA
-- Migrando os caminhos fixos para a tabela student_documents
INSERT INTO student_documents (name, type, status, file_path, student_id)
SELECT 'Diploma de Graduação', 'DIPLOMA', 'APROVADO', ps.diploma_file_path, s.id
FROM postgrad_students ps
JOIN students s ON s.cpf = ps.cpf
WHERE ps.diploma_file_path IS NOT NULL;

INSERT INTO student_documents (name, type, status, file_path, student_id)
SELECT 'RG/CPF', 'RG', 'APROVADO', ps.rg_cpf_file_path, s.id
FROM postgrad_students ps
JOIN students s ON s.cpf = ps.cpf
WHERE ps.rg_cpf_file_path IS NOT NULL;

-- 4. LIMPEZA (OPCIONAL: Manter por segurança até validação total)
-- DROP TABLE postgrad_students CASCADE;

-- 5. ÍNDICES DE PERFORMANCE PARA BUSCAS UNIFICADAS
CREATE INDEX IF NOT EXISTS idx_students_type_active ON students(student_type, active);
CREATE INDEX IF NOT EXISTS idx_students_email_lookup ON students(email);

-- 6. OTIMIZAÇÃO DE CURSOS
-- Adicionando flag para diferenciar cursos de Graduação e Pós
ALTER TABLE courses ADD COLUMN IF NOT EXISTS academic_level VARCHAR(20) DEFAULT 'GRADUATION';

-- 7. LOG DE SUCESSO
INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V40.0-OMEGA-NORMALIZATION', 'SUCCESS', 'PRODUCTION', 'Unificação de estudantes e normalização de documentos concluída.');
