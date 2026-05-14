-- V37.7-REPAIR-AUDIT-STABILIZER.sql
-- PROTOCOLO V37.7 - ESTABILIZAÇÃO DE AUDITORIA CAMPUS CARE
-- OBJETIVO: Sincronizar dados de auditoria e otimizar performance do módulo de reparos

DO $$ 
BEGIN 
    -- 1. Sincroniza fotos e cargos dos chamados baseados na tabela staff_members
    -- Onde o criador já está definido mas as informações de auditoria estão incompletas
    UPDATE repair_tickets rt
    SET creator_photo_url = s.foto_url,
        creator_position = s.position
    FROM staff_members s
    JOIN users u ON s.id = u.id
    WHERE rt.reported_by_id = u.id
      AND (rt.creator_photo_url IS NULL OR rt.creator_position IS NULL)
      AND s.foto_url IS NOT NULL;

    -- 2. Define fallback para auditoria caso os dados de staff não existam
    UPDATE repair_tickets
    SET creator_name = u.username,
        creator_position = 'USUÁRIO AUTORIZADO',
        creator_photo_url = 'default-auditor.png'
    FROM users u
    WHERE repair_tickets.reported_by_id = u.id
      AND repair_tickets.creator_name IS NULL;

    -- 3. Melhoria de Performance: Índices para busca rápida de chamados ativos e por localização
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_repair_active') THEN
        CREATE INDEX idx_repair_active ON repair_tickets(active) WHERE active = true;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_repair_status') THEN
        CREATE INDEX idx_repair_status ON repair_tickets(status);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_repair_location') THEN
        CREATE INDEX idx_repair_location ON repair_tickets(location);
    END IF;

END $$;
