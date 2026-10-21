-- Vincula a cobrança emitida à linha da tabela de valores (charge_prices) de onde o valor veio.
-- Aditiva: colunas nulas, cobranças antigas continuam válidas. Mudar a tabela depois NÃO altera faturas emitidas.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS charge_price_id BIGINT REFERENCES charge_prices(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_payments_charge_price ON payments (charge_price_id);
