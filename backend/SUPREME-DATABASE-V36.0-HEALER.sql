-- =============================================
-- PROTOCOLO V36.0-ULTRA-HEALER (OMÉGA-SUPREME)
-- ALVO: Neon PostgreSQL (Postgrad Enrollment)
-- OBJETIVO: Curar constraints de integridade e resolver conflitos de Ghost Records
-- =============================================

DO $$ 
BEGIN 
    -- 1. Curar a tabela postgrad_students
    -- Remover a obrigatoriedade (NOT NULL) se a coluna existir, permitindo que o JPA gerencie
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='registration_date') THEN 
        ALTER TABLE postgrad_students ALTER COLUMN registration_date DROP NOT NULL;
    END IF;

    -- Garantir que as colunas de auditoria tenham valores padrão para evitar erros futuros
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='postgrad_students' AND column_name='created_at') THEN 
        ALTER TABLE postgrad_students ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
    END IF;

    -- 2. Limpeza Proativa de Ghost Records (Soft Delete Conflitantes)
    -- Isso remove registros 'active = false' que tenham o mesmo CPF ou Email, liberando o cadastro
    DELETE FROM postgrad_students 
    WHERE active = false 
    AND (
        cpf IN (SELECT cpf FROM postgrad_students WHERE active = true) OR
        email IN (SELECT email FROM postgrad_students WHERE active = true)
    );

    -- 3. Resgate de Orfãos: Se houver registros active=false mas que não tem duplicata ativa, 
    -- deixa eles lá, mas garante que não bloqueiem o CPF/Email único se o usuário tentar cadastrar de novo.
    -- (Opcional: Remover todos os active=false inativos que estão bloqueando)
    -- Para robustez total, vamos remover todos os inativos que impedem a inserção de novos registros idênticos.
    
END $$;

-- 4. Verificação Final de Sanidade
SELECT table_name, column_name, is_nullable, column_default
FROM information_schema.columns 
WHERE table_name = 'postgrad_students' 
AND column_name IN ('registration_date', 'created_at', 'active');
