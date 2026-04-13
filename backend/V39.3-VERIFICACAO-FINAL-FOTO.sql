-- ================================================================
-- V39.3 — VERIFICAÇÃO FINAL: SISTEMA DE FOTO DO RESPONSÁVEL
-- Executa no Console SQL do Neon após reiniciar o backend
-- ================================================================

-- ----------------------------------------------------------------
-- VERIFICAÇÃO 1: Status de todas as fotos nos chamados recentes
-- ----------------------------------------------------------------
SELECT
    rt.id             AS ticket_id,
    rt.reported_by_name,
    rt.reported_by_role,
    CASE
        WHEN rt.reporter_photo_url IS NULL
            THEN '❌ NULL — StaffMember sem foto cadastrada'
        WHEN rt.reporter_photo_url = 'default-auditor.png'
            THEN '⚠️ string inválida legada'
        WHEN rt.reporter_photo_url LIKE 'staff-photos/%'
            THEN '✅ Path correto: ' || rt.reporter_photo_url
        ELSE '🔍 Outro formato: ' || rt.reporter_photo_url
    END               AS status_foto,
    rt.status,
    rt.created_at
FROM repair_tickets rt
ORDER BY rt.created_at DESC
LIMIT 15;

-- ----------------------------------------------------------------
-- VERIFICAÇÃO 2: StaffMember tem foto cadastrada?
-- ----------------------------------------------------------------
SELECT
    sm.id,
    sm.full_name,
    sm.position,
    CASE
        WHEN sm.foto_url IS NULL
            THEN '❌ Sem foto cadastrada (fotoUrl = NULL)'
        WHEN sm.foto_url LIKE 'staff-photos/%'
            THEN '✅ Path OK: ' || sm.foto_url
        ELSE '🔍 Formato inesperado: ' || sm.foto_url
    END AS status_foto,
    u.username AS login_sistema
FROM staff_members sm
LEFT JOIN users u ON u.id = sm.id
WHERE sm.active = TRUE
ORDER BY sm.id;

-- ----------------------------------------------------------------
-- VERIFICAÇÃO 3: Cruzamento — quem criou cada chamado e tem foto?
-- ----------------------------------------------------------------
SELECT
    rt.id          AS ticket_id,
    rt.title,
    rt.reported_by_name,
    rt.reporter_photo_url AS foto_no_chamado,
    sm.foto_url           AS foto_atual_no_staff,
    CASE
        WHEN sm.foto_url IS NULL
            THEN '❌ Staff sem foto — usuário precisa ter foto cadastrada no módulo Staff'
        WHEN rt.reporter_photo_url IS NULL AND sm.foto_url IS NOT NULL
            THEN '⚠️ Staff TEM foto mas chamado foi criado sem ela (antes do fix)'
        WHEN rt.reporter_photo_url = sm.foto_url
            THEN '✅ Foto sincronizada corretamente'
        ELSE '🔍 Foto divergente entre chamado e staff atual'
    END AS diagnostico
FROM repair_tickets rt
LEFT JOIN users u    ON u.id  = rt.reported_by_id
LEFT JOIN staff_members sm ON sm.id = u.id
WHERE rt.active = TRUE
ORDER BY rt.created_at DESC;

-- ================================================================
-- SE O RESULTADO MOSTRAR "Staff sem foto":
--   → Vá ao módulo Staff Members e cadastre a foto 3x4 do responsável.
--   → Novos chamados criados após isso exibirão a foto automaticamente.
--
-- SE O RESULTADO MOSTRAR "Staff TEM foto mas chamado foi criado sem ela":
--   → Execute a UPDATE abaixo para sincronizar registros antigos:
-- ================================================================

-- OPCIONAL: Sincronizar chamados antigos com a foto atual do StaffMember
-- (descomente e execute apenas se necessário)
/*
UPDATE repair_tickets rt
SET reporter_photo_url = sm.foto_url
FROM users u
JOIN staff_members sm ON sm.id = u.id
WHERE rt.reported_by_id = u.id
  AND rt.reporter_photo_url IS NULL
  AND sm.foto_url IS NOT NULL;
*/
