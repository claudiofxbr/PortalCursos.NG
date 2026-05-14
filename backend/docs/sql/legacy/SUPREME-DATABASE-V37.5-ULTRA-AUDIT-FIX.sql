-- SUPREME-DATABASE-V37.5-ULTRA-AUDIT-FIX.sql
-- PROTOCOLO V37.5 - SINCRONIZAÇÃO TOTAL DE AUDITORIA MEC
-- OBJETIVO: Preencher retroativamente a creator_photo_url usando os dados da tabela staff_members

DO $$ 
BEGIN 
    -- 1. Sincroniza fotos dos cursos onde o nome do criador coincide com o nome de um funcionário
    UPDATE courses c
    SET creator_photo_url = s.foto_url
    FROM staff_members s
    WHERE lower(trim(c.creator_name)) = lower(trim(s.full_name))
      AND c.creator_photo_url IS NULL
      AND s.foto_url IS NOT NULL;

    -- 2. Define uma foto padrão para auditoria caso o responsável ainda não tenha foto no perfil
    -- Isso garante que o selo de auditoria nunca fique vazio
    UPDATE courses
    SET creator_photo_url = 'default-auditor.png'
    WHERE creator_photo_url IS NULL 
      AND creator_name IS NOT NULL;

    -- 3. Garante que campos de auditoria não sejam nulos para futuros registros (Opcional, mas recomendado)
    -- ALTER TABLE courses ALTER COLUMN creator_name SET NOT NULL;
END $$;
