-- V11__Fix_StaffMembers_BaseAudit_Columns.sql
-- Adiciona colunas de BaseAuditEntity que faltam em staff_members,
-- causando erro 500 em qualquer INSERT/UPDATE via JPA.

-- active: herdado de BaseAuditEntity, não foi adicionado em nenhuma migration anterior
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- registration_date: @PrePersist do BaseAuditEntity define este campo
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP;

-- last_modified_by: @LastModifiedBy do Spring Data Auditing
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

-- creator_id: FK para staff_members (self-referência de auditoria)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'staff_members' AND column_name = 'creator_id'
    ) THEN
        ALTER TABLE staff_members ADD COLUMN creator_id BIGINT;
        ALTER TABLE staff_members ADD CONSTRAINT fk_staff_creator_v11
            FOREIGN KEY (creator_id) REFERENCES staff_members(id);
    END IF;
END $$;

-- Índice de performance para filtro de ativos (usado em findAllByActiveTrue)
CREATE INDEX IF NOT EXISTS idx_staff_members_active ON staff_members(active);
