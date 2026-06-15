-- V9__Fix_Id_Types_To_Bigint.sql
-- Converte colunas id de INTEGER para BIGINT (compatibilidade com JPA @GeneratedValue Long)
-- Drop de FKs necessárias, conversão de tipos, e recriação das FKs.

-- ===== 1. DROP de FOREIGN KEYS que bloqueiam ALTER TYPE =====

-- FKs referenciando postgrad_students.id
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_postgrad_student_id_fkey;

-- FKs referenciando repair_tickets.id
ALTER TABLE repair_photos DROP CONSTRAINT IF EXISTS fk_repair_photos_ticket;

-- FKs referenciando roles.id
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS fk_user_roles_role;

-- FKs referenciando students.id
ALTER TABLE payments        DROP CONSTRAINT IF EXISTS payments_student_id_fkey;
ALTER TABLE student_documents DROP CONSTRAINT IF EXISTS student_documents_student_id_fkey;

-- FKs referenciando users.id (e also users.id FK columns precisam ser BIGINT)
ALTER TABLE repair_tickets DROP CONSTRAINT IF EXISTS repair_tickets_reported_by_id_fkey;
ALTER TABLE staff_members  DROP CONSTRAINT IF EXISTS staff_members_user_id_fkey;
ALTER TABLE students       DROP CONSTRAINT IF EXISTS students_user_id_fkey;
ALTER TABLE user_roles     DROP CONSTRAINT IF EXISTS fk_user_roles_user;
ALTER TABLE user_sessions  DROP CONSTRAINT IF EXISTS user_sessions_user_id_fkey;

-- FKs auto-referenciadas em staff_members (creator_id)
ALTER TABLE staff_members DROP CONSTRAINT IF EXISTS fk_staff_creator;

-- FKs em payments referenciando staff_members (creator_id)
ALTER TABLE payments      DROP CONSTRAINT IF EXISTS fk_payment_creator;
ALTER TABLE students      DROP CONSTRAINT IF EXISTS fk_student_creator;
ALTER TABLE repair_tickets DROP CONSTRAINT IF EXISTS fk_repair_creator;
ALTER TABLE courses       DROP CONSTRAINT IF EXISTS fk_courses_creator;

-- ===== 2. ALTERAR TIPOS DE id PARA BIGINT =====

ALTER TABLE users             ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE roles             ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE students          ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE postgrad_students ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE payments          ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE repair_tickets    ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE student_documents ALTER COLUMN id TYPE BIGINT USING id::BIGINT;
ALTER TABLE user_sessions     ALTER COLUMN id TYPE BIGINT USING id::BIGINT;

-- Converter sequences para BIGINT (evita overflow)
ALTER SEQUENCE IF EXISTS users_id_seq              AS BIGINT;
ALTER SEQUENCE IF EXISTS roles_id_seq              AS BIGINT;
ALTER SEQUENCE IF EXISTS students_id_seq           AS BIGINT;
ALTER SEQUENCE IF EXISTS postgrad_students_id_seq  AS BIGINT;
ALTER SEQUENCE IF EXISTS payments_id_seq           AS BIGINT;
ALTER SEQUENCE IF EXISTS repair_tickets_id_seq     AS BIGINT;
ALTER SEQUENCE IF EXISTS student_documents_id_seq  AS BIGINT;
ALTER SEQUENCE IF EXISTS user_sessions_id_seq      AS BIGINT;

-- ===== 3. RECRIAR FOREIGN KEYS =====

ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_role
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE;

ALTER TABLE user_sessions ADD CONSTRAINT user_sessions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE students ADD CONSTRAINT students_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE staff_members ADD CONSTRAINT staff_members_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE payments ADD CONSTRAINT payments_student_id_fkey
    FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE payments ADD CONSTRAINT payments_postgrad_student_id_fkey
    FOREIGN KEY (postgrad_student_id) REFERENCES postgrad_students(id);

ALTER TABLE student_documents ADD CONSTRAINT student_documents_student_id_fkey
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE;

ALTER TABLE repair_photos ADD CONSTRAINT fk_repair_photos_ticket
    FOREIGN KEY (repair_ticket_id) REFERENCES repair_tickets(id) ON DELETE CASCADE;

-- FKs de creator_id para staff_members (staff_members.id permanece BIGINT — já era)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='staff_members' AND column_name='creator_id') THEN
        ALTER TABLE staff_members ADD CONSTRAINT fk_staff_creator
            FOREIGN KEY (creator_id) REFERENCES staff_members(id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='students' AND column_name='creator_id') THEN
        ALTER TABLE students ADD CONSTRAINT fk_student_creator
            FOREIGN KEY (creator_id) REFERENCES staff_members(id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='creator_id') THEN
        ALTER TABLE repair_tickets ADD CONSTRAINT fk_repair_creator
            FOREIGN KEY (creator_id) REFERENCES staff_members(id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='creator_id') THEN
        ALTER TABLE payments ADD CONSTRAINT fk_payment_creator
            FOREIGN KEY (creator_id) REFERENCES staff_members(id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='courses' AND column_name='creator_id') THEN
        ALTER TABLE courses ADD CONSTRAINT fk_courses_creator
            FOREIGN KEY (creator_id) REFERENCES staff_members(id);
    END IF;
END $$;

-- repair_tickets.reported_by_id -> users.id (se coluna ainda existe)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='repair_tickets' AND column_name='reported_by_id') THEN
        ALTER TABLE repair_tickets ADD CONSTRAINT repair_tickets_reported_by_id_fkey
            FOREIGN KEY (reported_by_id) REFERENCES users(id);
    END IF;
END $$;
