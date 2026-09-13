-- Corrige FKs para users(id) que ficaram sem ON DELETE (RESTRICT/NO ACTION por padrão),
-- diferente do padrão já usado no resto do schema (students.user_id, staff_members.user_id
-- usam ON DELETE SET NULL — ver V9). Sem isso, UserService.deleteUser() (hard delete) lança
-- DataIntegrityViolationException sempre que o usuário tem RepairTicket.reported_by_id ou
-- DataDeletionRequest.user_id apontando pra ele.

-- data_deletion_requests.user_id era NOT NULL (V17) — precisa aceitar NULL para permitir
-- ON DELETE SET NULL quando o usuário referenciado for removido.
ALTER TABLE data_deletion_requests ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE data_deletion_requests DROP CONSTRAINT IF EXISTS fk_data_deletion_requests_user;
ALTER TABLE data_deletion_requests ADD CONSTRAINT fk_data_deletion_requests_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE repair_tickets DROP CONSTRAINT IF EXISTS repair_tickets_reported_by_id_fkey;
ALTER TABLE repair_tickets ADD CONSTRAINT repair_tickets_reported_by_id_fkey
    FOREIGN KEY (reported_by_id) REFERENCES users(id) ON DELETE SET NULL;
