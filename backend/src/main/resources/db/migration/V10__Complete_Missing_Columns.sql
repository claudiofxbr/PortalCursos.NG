-- V10__Complete_Missing_Columns.sql
-- Adiciona colunas que existem nas entidades JPA mas faltam no banco legado

-- payments: studentPhotoUrl (mapeado como student_photo_url TEXT)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS student_photo_url TEXT;

-- repair_tickets: colunas que podem existir no modelo mas não no banco
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_name  VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reported_by_role  VARCHAR(255);
ALTER TABLE repair_tickets ADD COLUMN IF NOT EXISTS reporter_photo_url TEXT;

-- staff_members: pode ter coluna version faltando
ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
