-- V4__Add_Missing_Audit_Columns.sql
-- Adiciona last_modified_by nas tabelas de negócio

ALTER TABLE courses       ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE payments      ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
