-- Suporte a pagamento por cartão (checkout hospedado do PagBank), aditivo e sem impacto no PIX:
--  * psp_checkout_id: id do checkout (CHEC_...), separado de psp_order_id (ORDE_..., usado pelo PIX)
--    para o webhook nunca precisar adivinhar o tipo do id.
--  * charged_amount_cents: valor em centavos fixado no momento da criação do checkout. A baixa compara
--    o valor pago com este, e não com o total recalculado no dia (juros de fatura vencida mudam por dia).
-- Colunas anuláveis: faturas existentes e pagamentos por PIX/boleto continuam válidos.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS psp_checkout_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS charged_amount_cents BIGINT;
ALTER TABLE payments ADD CONSTRAINT chk_payments_charged_amount_positive
    CHECK (charged_amount_cents IS NULL OR charged_amount_cents > 0);
