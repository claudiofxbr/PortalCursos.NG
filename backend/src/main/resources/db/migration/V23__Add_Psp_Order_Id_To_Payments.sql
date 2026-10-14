-- Adiciona coluna para guardar o id do pedido PIX criado no PagBank (PSP), usado na
-- reconciliação posterior via webhook assíncrono de confirmação de pagamento.
-- Sem FK (id de sistema externo, não referencia tabela local) e sem UNIQUE/índice
-- (decisão explícita: sem garantia formal de unicidade documentada pelo PSP; pode virar
-- índice único parcial numa migração futura se necessário).
ALTER TABLE payments ADD COLUMN IF NOT EXISTS psp_order_id VARCHAR(64);
