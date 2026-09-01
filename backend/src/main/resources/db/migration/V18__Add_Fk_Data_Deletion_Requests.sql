-- Adiciona FK ausente em data_deletion_requests.user_id -> users.id.
-- Seguro: DataAnonymizationService nunca deleta a linha do usuário (só mascara e
-- salva), então nenhuma linha órfã existente deve violar a constraint.
ALTER TABLE data_deletion_requests
    ADD CONSTRAINT fk_data_deletion_requests_user
    FOREIGN KEY (user_id) REFERENCES users(id);
