-- Hora da última sincronização do collector (atualizada a cada envio, mesmo quando todas as mensagens já existiam).
-- Evita que a Torre trate "nada novo para gravar" como "collector parado".
ALTER TABLE token_plan_config ADD COLUMN last_ingest_at TIMESTAMP WITH TIME ZONE;
