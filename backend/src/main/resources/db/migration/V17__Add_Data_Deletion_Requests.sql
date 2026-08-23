-- Solicitações de eliminação de dados pelo titular (LGPD art. 18, VI / GDPR art. 17).
CREATE TABLE IF NOT EXISTS data_deletion_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    requested_username VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_notes VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_data_deletion_requests_user_id ON data_deletion_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_data_deletion_requests_status ON data_deletion_requests(status);
