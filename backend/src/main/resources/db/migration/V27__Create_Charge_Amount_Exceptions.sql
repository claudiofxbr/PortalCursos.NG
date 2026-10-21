-- Pedidos de exceção ao valor da tabela de valores. O valor de uma cobrança vem sempre da tabela
-- (charge_prices); só a secretária do curso pode pedir um valor diferente, e o pedido só vira cobrança
-- depois de aprovado por um administrador. Aditiva: não altera nenhuma tabela existente.
CREATE TABLE IF NOT EXISTS charge_amount_exceptions (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    academic_level VARCHAR(20) NOT NULL CHECK (academic_level IN ('GRADUATION', 'POSTGRADUATE')),
    charge_price_id BIGINT NOT NULL REFERENCES charge_prices(id) ON DELETE RESTRICT,
    table_amount NUMERIC(12,2) NOT NULL CHECK (table_amount >= 0),
    requested_amount NUMERIC(12,2) NOT NULL CHECK (requested_amount >= 0),
    due_date DATE NOT NULL,
    description VARCHAR(500),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by VARCHAR(100) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by VARCHAR(100),
    decided_at TIMESTAMP,
    decision_reason VARCHAR(500),
    payment_id BIGINT REFERENCES payments(id) ON DELETE SET NULL
);

-- No máximo um pedido pendente por aluno e procedimento.
CREATE UNIQUE INDEX IF NOT EXISTS uq_charge_amount_exceptions_one_pending
    ON charge_amount_exceptions (student_id, charge_price_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_charge_amount_exceptions_status ON charge_amount_exceptions (status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_charge_amount_exceptions_requester ON charge_amount_exceptions (requested_by);
