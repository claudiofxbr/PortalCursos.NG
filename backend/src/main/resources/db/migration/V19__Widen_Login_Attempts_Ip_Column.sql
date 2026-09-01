-- LoginAttemptService agora recebe chaves prefixadas por escopo (ex.: "signup:"+ip,
-- "refresh:"+ip) além do IP puro do login. VARCHAR(45) comportava só o maior IPv6
-- possível (45 chars); com o prefixo "refresh:" (8 chars) um IPv6 completo estoura a
-- coluna e o INSERT falha. Alargamos para acomodar o maior prefixo + IPv6.
ALTER TABLE login_attempts ALTER COLUMN ip_address TYPE VARCHAR(64);
