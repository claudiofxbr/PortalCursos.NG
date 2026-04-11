-- ==========================================================
-- SCRIPT DE MIGRAÇÃO: PROTOCOLO SUPREME V30.9
-- OBJETIVO: ADICIONAR COLUNAS DE AUDITORIA E SOFT DELETE
-- ==========================================================

-- 1. ADICIONAR COLUNAS NAS TABELAS ACADÊMICAS (STUDENTS)
ALTER TABLE students ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE students ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 2. ADICIONAR COLUNAS NAS TABELAS DE PÓS-GRADUAÇÃO (POSTGRAD_STUDENTS)
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE postgrad_students ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 3. ADICIONAR COLUNAS NA TABELA DE PAGAMENTOS (PAYMENTS)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 4. ADICIONAR COLUNAS NA TABELA DE CURSOS (COURSES) - já existe active, mas garantir updated_at
ALTER TABLE courses ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 5. ÍNDICES PARCIAIS PARA SOFT DELETE (PERFORMANCE SUPREME)
CREATE INDEX IF NOT EXISTS idx_students_active_lookup ON students(id) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_postgrad_students_active_lookup ON postgrad_students(id) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_payments_active_lookup ON payments(id) WHERE active = true;

-- 6. ÍNDICE DE BUSCA RÁPIDA POR CPF (ATIVOS)
CREATE INDEX IF NOT EXISTS idx_students_cpf_active ON students(cpf) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_postgrad_students_cpf_active ON postgrad_students(cpf) WHERE active = true;

-- 7. REGISTRO DE EXECUÇÃO
INSERT INTO deployment_logs (version, status, environment, summary) 
VALUES ('V30.9-INDEXES', 'SUCCESS', 'DATABASE', 'Índices parciais e colunas de auditoria aplicados com sucesso.');
