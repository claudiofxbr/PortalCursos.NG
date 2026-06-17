-- V12: Limpa todos os bloqueios de IP acumulados em login_attempts.
-- Necessário porque o IP do administrador foi bloqueado por tentativas
-- de login durante o processo de debug/deploy inicial, impedindo o acesso.
-- O NeonKeepAliveTask remove registros expirados automaticamente no futuro.

DELETE FROM login_attempts WHERE blocked_until IS NOT NULL;
DELETE FROM login_attempts WHERE last_attempt < NOW() - INTERVAL '1 hour';
