-- ================================================================
-- V39.2 — DIAGNÓSTICO PROFUNDO: FOTO DO RESPONSÁVEL
-- Executa no Console SQL do Neon para identificar a causa real
-- ================================================================

-- ----------------------------------------------------------------
-- DIAGNÓSTICO 1: O que está salvo em reporter_photo_url?
-- ----------------------------------------------------------------
SELECT
    id,
    reported_by_name,
    reported_by_role,
    COALESCE(reporter_photo_url, '⚠️ NULL — sem foto') AS foto_url_atual,
    status,
    created_at
FROM repair_tickets
ORDER BY created_at DESC
LIMIT 10;

-- ----------------------------------------------------------------
-- DIAGNÓSTICO 2: Qual é o ID do User que criou os chamados?
-- (para verificar se o StaffMember corresponde)
-- ----------------------------------------------------------------
SELECT
    rt.id          AS ticket_id,
    rt.reported_by_name,
    rt.reporter_photo_url,
    u.id           AS user_id,
    u.username
FROM repair_tickets rt
LEFT JOIN users u ON u.id = rt.reported_by_id
ORDER BY rt.created_at DESC
LIMIT 10;

-- ----------------------------------------------------------------
-- DIAGNÓSTICO 3: O StaffMember do criador TEM foto cadastrada?
-- ----------------------------------------------------------------
SELECT
    sm.id,
    sm.full_name,
    sm.position,
    COALESCE(sm.foto_url, '⚠️ NULL — Staff sem foto cadastrada') AS foto_url,
    sm.department
FROM staff_members sm
ORDER BY sm.id;

-- ================================================================
-- COMO INTERPRETAR:
-- Se Diagnóstico 3 mostrar "NULL" => O StaffMember não tem foto.
--   Solução: Cadastrar foto do responsável em Staff Members.
-- Se Diagnóstico 3 mostrar um path (ex: staff-photos/abc.jpg) =>
--   O path está correto no banco; o problema é de URL no frontend.
-- ================================================================
