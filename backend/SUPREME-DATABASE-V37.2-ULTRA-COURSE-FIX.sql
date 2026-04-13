-- =============================================
-- POLÍTICA DE AUDITORIA MEC - PROTOCOLO V37.2-ULTRA
-- REPARO DE INFRAESTRUTURA DE DADOS DE CURSOS
-- DATA: 13/04/2026
-- =============================================

-- 1. Sincronização de Schema (Rastreabilidade de Emissor)
-- Adiciona suporte a auditoria visual e controle de atividade na tabela de cursos
ALTER TABLE courses ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS creator_name VARCHAR(255);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS creator_position VARCHAR(255);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS creator_photo_url TEXT;

-- 2. Sincronização de Metadados Temporais (Conformidade com BaseAuditEntity)
ALTER TABLE courses ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- 3. Otimização de Índices (Resiliência de Busca)
CREATE INDEX IF NOT EXISTS idx_courses_active ON courses(active);
CREATE INDEX IF NOT EXISTS idx_courses_creator ON courses(creator_name);

-- 4. Normalização de Dados Existentes (Opcional - Ativa cursos antigos)
UPDATE courses SET active = TRUE WHERE active IS NULL;

-- 5. Comentários de Documentação Interna (Audit Data Dictionary)
COMMENT ON COLUMN courses.creator_name IS 'Nome do staff responsável pelo cadastro do curso (MEC Audit)';
COMMENT ON COLUMN courses.creator_position IS 'Cargo do emissor do registro no momento da criação';
COMMENT ON COLUMN courses.creator_photo_url IS 'Caminho absoluto ou relativo da foto 3x4 do emissor';
COMMENT ON COLUMN courses.registration_date IS 'Data oficial de registro institucional';
