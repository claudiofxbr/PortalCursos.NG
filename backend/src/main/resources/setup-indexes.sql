-- SCRIPTS DE OTIMIZAÇÃO DE BANCO DE DADOS (NG-02 V8.0)
-- Execute estes comandos no seu Console do Neon para garantir performance máxima.

-- 1. Índices para Busca de Usuário (Acelera o Login)
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- 2. Índices para Sessões e Refresh Tokens (Evita erros de timeout na renovação de token)
CREATE INDEX IF NOT EXISTS idx_user_sessions_refresh_token ON user_sessions(refresh_token);
CREATE INDEX IF NOT EXISTS idx_user_sessions_expiry_date ON user_sessions(expiry_date);

-- 3. Índices para Membros da Staff (Acelera carregamento do dashboard)
CREATE INDEX IF NOT EXISTS idx_staff_members_user_id ON staff_members(user_id);

-- 4. Índices para Tickets de Reparo (Acelera listagem de infraestrutura)
CREATE INDEX IF NOT EXISTS idx_repair_tickets_status ON repair_tickets(status);
CREATE INDEX IF NOT EXISTS idx_repair_tickets_user_id ON repair_tickets(reported_by_id);
CREATE INDEX IF NOT EXISTS idx_repair_tickets_created ON repair_tickets(created_at);

-- 5. Índices para Fotos de Reparo (Acelera join de evidências)
CREATE INDEX IF NOT EXISTS idx_repair_photos_ticket_id ON repair_photos(repair_ticket_id);

-- Otimização concluída.
