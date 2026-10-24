-- OTIMIZAÇÃO DE BANCO DE DADOS V9.0 - PORTAL CURSOS
-- Executar este script no Console do Neon para garantir performance máxima.

-- Índices para Autenticação (Tabela Users)
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Índices para Membros da Equipe (Staff)
CREATE INDEX IF NOT EXISTS idx_staff_username ON staff_members(username);

-- Índices para Estudantes (Student)
CREATE INDEX IF NOT EXISTS idx_student_user_id ON students(user_id);
CREATE INDEX IF NOT EXISTS idx_student_cpf ON students(cpf);

-- Índices para Sessões (Prevenção de lentidão no check de token)
CREATE INDEX IF NOT EXISTS idx_usersession_token ON user_sessions(token);
CREATE INDEX IF NOT EXISTS idx_usersession_user_id ON user_sessions(user_id);

-- Otimização de busca em Cursos
CREATE INDEX IF NOT EXISTS idx_course_title ON courses(title);

ANALYZE;
