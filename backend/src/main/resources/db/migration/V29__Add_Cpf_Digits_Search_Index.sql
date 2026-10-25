-- Achado B7 da auditoria: a busca de alunos por CPF filtra por REPLACE(REPLACE(cpf,'.',''),'-','') LIKE 'digitos%'.
-- O índice idx_students_cpf (V13) é sobre a coluna crua e não serve a essa expressão, então a busca lia a tabela inteira.
-- Índice de expressão com text_pattern_ops: o prefixo do LIKE só usa índice com essa classe de operador (o collation do banco pode não ser C).
-- Aditiva e idempotente: só cria índice; não altera dados nem tabelas. Cobre graduação e pós (mesma tabela students).

CREATE INDEX IF NOT EXISTS idx_students_cpf_digits
    ON students ((REPLACE(REPLACE(cpf, '.', ''), '-', '')) text_pattern_ops);
