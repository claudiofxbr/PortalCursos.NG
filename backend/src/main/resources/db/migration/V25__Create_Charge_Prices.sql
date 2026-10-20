-- Tabela de valores das cobranças (seção 11 do ExportaçãoPortalCursos.NG.md).
-- Aditiva: não toca em payments nem em nenhuma tabela existente. current_value NULL = "a preencher".

CREATE TABLE IF NOT EXISTS charge_prices (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    category VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    academic_scope VARCHAR(20) NOT NULL CHECK (academic_scope IN ('ALL', 'GRADUATION', 'POSTGRAD')),
    course_id UUID REFERENCES courses(id) ON DELETE RESTRICT,
    current_value NUMERIC(12,2) CHECK (current_value IS NULL OR current_value >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(100)
);

-- Uma cobrança ativa por (código, curso); curso nulo = valor global ("Todos").
CREATE UNIQUE INDEX IF NOT EXISTS uq_charge_prices_code_course
    ON charge_prices (code, COALESCE(course_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS charge_price_changes (
    id BIGSERIAL PRIMARY KEY,
    charge_price_id BIGINT NOT NULL REFERENCES charge_prices(id) ON DELETE CASCADE,
    proposed_value NUMERIC(12,2) NOT NULL CHECK (proposed_value >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    proposed_by VARCHAR(100) NOT NULL,
    proposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    decided_by VARCHAR(100),
    decided_at TIMESTAMP,
    decision_reason VARCHAR(500)
);

-- No máximo uma proposta pendente por cobrança.
CREATE UNIQUE INDEX IF NOT EXISTS uq_charge_price_changes_one_pending
    ON charge_price_changes (charge_price_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_charge_price_changes_price ON charge_price_changes (charge_price_id);

-- Quem atende cada curso e quem é chefe da secretaria naquele curso.
CREATE TABLE IF NOT EXISTS staff_course_scope (
    id BIGSERIAL PRIMARY KEY,
    staff_id BIGINT NOT NULL REFERENCES staff_members(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    is_head BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (staff_id, course_id)
);
CREATE INDEX IF NOT EXISTS idx_staff_course_scope_staff ON staff_course_scope (staff_id);

-- Carga inicial: 24 cobranças globais, valores em branco (idempotente).
INSERT INTO charge_prices (code, category, name, academic_scope)
SELECT v.code, v.category, v.name, v.academic_scope
FROM (VALUES
    ('DOC_CIVIL_ESCOLAR', 'Ingresso e Matrícula', 'Entrega de documentação civil e escolar', 'ALL'),
    ('COMPROVACAO_ESCOLARIDADE', 'Ingresso e Matrícula', 'Comprovação de escolaridade anterior', 'ALL'),
    ('CONTRATO_ACEITE', 'Ingresso e Matrícula', 'Assinatura do contrato e aceite regimental', 'ALL'),
    ('REMATRICULA', 'Renovação e Ajustes Curriculares', 'Rematrícula periódica', 'ALL'),
    ('AJUSTE_MATRICULA', 'Renovação e Ajustes Curriculares', 'Ajuste de matrícula', 'ALL'),
    ('APROVEITAMENTO_ESTUDOS', 'Renovação e Ajustes Curriculares', 'Aproveitamento de estudos e equivalências', 'ALL'),
    ('TRANCAMENTO_CANCELAMENTO', 'Renovação e Ajustes Curriculares', 'Trancamento ou cancelamento de curso', 'ALL'),
    ('ATUALIZACAO_CADASTRAL', 'Acompanhamento da Vida Escolar', 'Atualização cadastral contínua', 'ALL'),
    ('JUSTIFICATIVAS_REGIMES', 'Acompanhamento da Vida Escolar', 'Justificativas e regimes especiais', 'ALL'),
    ('SEGUNDA_CHAMADA', 'Acompanhamento da Vida Escolar', 'Protocolos de segunda chamada', 'ALL'),
    ('ATIVIDADES_COMPLEMENTARES', 'Acompanhamento da Vida Escolar', 'Atividades complementares', 'GRADUATION'),
    ('REGISTRO_ORIENTACAO', 'Exigências Específicas da Pós-Graduação', 'Registro de orientação', 'POSTGRAD'),
    ('EXAME_QUALIFICACAO', 'Exigências Específicas da Pós-Graduação', 'Exame de qualificação', 'POSTGRAD'),
    ('COMPOSICAO_BANCAS', 'Exigências Específicas da Pós-Graduação', 'Composição de bancas', 'POSTGRAD'),
    ('PROFICIENCIA_LINGUA', 'Exigências Específicas da Pós-Graduação', 'Comprovação de proficiência em língua estrangeira', 'POSTGRAD'),
    ('ESTAGIO_TCC', 'Conclusão e Expedição de Documentos', 'Comprovação de estágio obrigatório e TCC', 'ALL'),
    ('NADA_CONSTA', 'Conclusão e Expedição de Documentos', 'Certidão de nada consta', 'ALL'),
    ('COLACAO_DIPLOMACAO', 'Conclusão e Expedição de Documentos', 'Requerimento de colação de grau ou diplomação', 'ALL'),
    ('TUITION', 'Cobranças recorrentes', 'Mensalidade', 'ALL'),
    ('ENROLLMENT_FEE', 'Cobranças recorrentes', 'Taxa de matrícula', 'ALL'),
    ('DOCUMENT_REQUEST', 'Cobranças recorrentes', 'Solicitação de documento', 'ALL'),
    ('LAB_FEE', 'Cobranças recorrentes', 'Taxa de laboratório', 'ALL'),
    ('INTERNAL_PROCESS', 'Cobranças recorrentes', 'Processo interno', 'ALL'),
    ('OTHER', 'Cobranças recorrentes', 'Outras', 'ALL')
) AS v(code, category, name, academic_scope)
WHERE NOT EXISTS (
    SELECT 1 FROM charge_prices p WHERE p.code = v.code AND p.course_id IS NULL AND p.active = TRUE
);
