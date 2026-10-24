-- Índices das FKs das tabelas de valores (V25) e de exceções de valor (V27) que ficaram sem índice próprio
-- (achado M4 da auditoria). Sem eles, excluir/consultar por curso, aluno ou fatura vira varredura da tabela inteira.
-- Aditiva e idempotente: só cria índices; não altera dados nem tabelas.

-- charge_prices.course_id: o índice único existente começa por "code" e é parcial (WHERE active), não serve ao curso.
CREATE INDEX IF NOT EXISTS idx_charge_prices_course ON charge_prices (course_id);

-- staff_course_scope: UNIQUE (staff_id, course_id) cobre buscas por colaborador, não por curso (ON DELETE CASCADE de courses).
CREATE INDEX IF NOT EXISTS idx_staff_course_scope_course ON staff_course_scope (course_id);

-- charge_amount_exceptions: o único índice por aluno/procedimento é parcial (WHERE status = 'PENDING') e não atende
-- o histórico (aprovadas e rejeitadas) nem as checagens das FKs RESTRICT para students e charge_prices.
CREATE INDEX IF NOT EXISTS idx_charge_amount_exceptions_student ON charge_amount_exceptions (student_id);
CREATE INDEX IF NOT EXISTS idx_charge_amount_exceptions_price ON charge_amount_exceptions (charge_price_id);
CREATE INDEX IF NOT EXISTS idx_charge_amount_exceptions_payment ON charge_amount_exceptions (payment_id);
