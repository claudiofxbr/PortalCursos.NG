-- Orçamento mensal opcional. NULL = usar a referência estimada (limite semanal x dias do mês / 7).
ALTER TABLE token_plan_config ADD COLUMN monthly_limit_tokens BIGINT;
ALTER TABLE token_plan_config ADD CONSTRAINT ck_plan_config_monthly CHECK (monthly_limit_tokens IS NULL OR monthly_limit_tokens > 0);
