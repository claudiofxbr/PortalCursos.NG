-- ==========================================================
-- V15: Corrige dois defeitos críticos encontrados na auditoria
-- de banco de dados (código+BD, ago/2026).
--
-- 1. `secretary_process_type` foi criada como VARCHAR(20) em V1,
--    mas o enum Java ESecretaryProcessType tem o valor
--    ENROLLMENT_CANCELLATION (23 caracteres). Qualquer Payment
--    salvo com esse valor falha em runtime com
--    "value too long for type character varying(20)".
--
-- 2. V6 introduziu `student_type` com DEFAULT 'UNDERGRAD', mas a
--    aplicação só reconhece os discriminadores 'GRADUATION' e
--    'POSTGRAD' (@DiscriminatorValue). Todo aluno cadastrado antes
--    da V6 recebeu 'UNDERGRAD' e ficou invisível/inacessível nas
--    consultas de single-table inheritance. Como não existem
--    alunos de pós anteriores à unificação (introduzida na própria
--    V6), qualquer linha 'UNDERGRAD' é necessariamente de graduação.
--
-- 3. `active` foi adicionado como NULL-ável (BOOLEAN DEFAULT TRUE,
--    sem NOT NULL) em students/courses/payments/repair_tickets na V5
--    — só staff_members foi corrigido para NOT NULL na V11. Toda
--    consulta de soft-delete filtra com "active = true", então uma
--    linha com active NULL some silenciosamente da aplicação sem
--    nunca ter sido de fato removida.
-- ==========================================================

ALTER TABLE payments ALTER COLUMN secretary_process_type TYPE VARCHAR(30);

UPDATE students SET student_type = 'GRADUATION' WHERE student_type = 'UNDERGRAD';

UPDATE students       SET active = TRUE WHERE active IS NULL;
UPDATE courses        SET active = TRUE WHERE active IS NULL;
UPDATE payments       SET active = TRUE WHERE active IS NULL;
UPDATE repair_tickets SET active = TRUE WHERE active IS NULL;

ALTER TABLE students       ALTER COLUMN active SET DEFAULT TRUE, ALTER COLUMN active SET NOT NULL;
ALTER TABLE courses        ALTER COLUMN active SET DEFAULT TRUE, ALTER COLUMN active SET NOT NULL;
ALTER TABLE payments       ALTER COLUMN active SET DEFAULT TRUE, ALTER COLUMN active SET NOT NULL;
ALTER TABLE repair_tickets ALTER COLUMN active SET DEFAULT TRUE, ALTER COLUMN active SET NOT NULL;
