-- V8: Tabela de controle de tentativas de login (brute-force protection persistente)
-- Substitui o ConcurrentHashMap in-memory do LoginAttemptService.

CREATE TABLE IF NOT EXISTS login_attempts (
    ip_address   VARCHAR(45)  NOT NULL PRIMARY KEY,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP WITH TIME ZONE,
    last_attempt  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_blocked_until ON login_attempts(blocked_until);

-- Limpeza automática via função + cron (opcional, executado pelo NeonKeepAliveTask)
-- Registros com bloqueio expirado há mais de 24h podem ser removidos com segurança.
