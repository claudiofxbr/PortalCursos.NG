-- Registro de consentimento LGPD (Art. 8º)/GDPR (Art. 7) na conta do usuário.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS privacy_consent_accepted BOOLEAN,
    ADD COLUMN IF NOT EXISTS privacy_consent_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS privacy_consent_at TIMESTAMP;
