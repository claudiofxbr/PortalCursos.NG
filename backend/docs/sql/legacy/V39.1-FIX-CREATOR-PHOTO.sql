-- ================================================================
-- V39.1 — CORREÇÃO EMERGENCIAL: FOTO DO RESPONSÁVEL DO CHAMADO
-- Problema: Registros antigos têm reporter_photo_url = 'default-auditor.png'
--           Esta string literal não é uma URL válida; o frontend não consegue
--           construir uma URL de storage a partir dela.
-- Solução:  Zerá-la para NULL. O frontend exibirá o ícone 👤 corretamente.
-- EXECUTAR NO CONSOLE SQL DO NEON
-- ================================================================

-- ----------------------------------------------------------------
-- PASSO 1: Diagnóstico — quantos registros têm o valor inválido?
-- ----------------------------------------------------------------
SELECT
    COUNT(*) AS total_invalidos,
    'reporter_photo_url = default-auditor.png' AS causa
FROM repair_tickets
WHERE reporter_photo_url = 'default-auditor.png';

-- ----------------------------------------------------------------
-- PASSO 2: Correção — apaga o valor inválido (seta NULL)
--          O frontend usará o ícone padrão 👤.
-- ----------------------------------------------------------------
UPDATE repair_tickets
SET reporter_photo_url = NULL
WHERE reporter_photo_url = 'default-auditor.png'
   OR reporter_photo_url = 'default-auditor.png'
   OR reporter_photo_url LIKE 'default-%'        -- previne variações futuras
   OR reporter_photo_url LIKE '%.png'            -- arquivos PNG locais sem path de storage
      AND reporter_photo_url NOT LIKE 'http%'    -- não é URL absoluta válida
      AND reporter_photo_url NOT LIKE 'staff-%'  -- não é path de storage válido
      AND reporter_photo_url NOT LIKE 'photos/%' -- não é path de storage válido
;

-- ----------------------------------------------------------------
-- PASSO 3: Verificação pós-correção
-- ----------------------------------------------------------------
SELECT
    id,
    reported_by_name,
    reported_by_role,
    reporter_photo_url,
    status,
    created_at
FROM repair_tickets
ORDER BY created_at DESC
LIMIT 20;

-- ================================================================
-- FIM DO SCRIPT V39.1
-- ================================================================
