-- V20__Add_Missing_Fk_Indexes.sql
-- Auditoria (item 3.2): colunas de FK sem índice forçam sequential scan em
-- deleções em cascata e joins. As demais FKs citadas na auditoria já possuem
-- índice criado em migrations anteriores:
--   students(creator_id)      -> idx_students_creator      (V3)
--   students(course_id)       -> idx_students_course_id    (V7)
--   repair_tickets(creator_id)-> idx_repairs_creator       (V3)
--   courses(creator_id)       -> idx_courses_creator       (V3)
-- Falta apenas o índice da FK user_sessions(user_id) -> users(id) ON DELETE CASCADE.

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
