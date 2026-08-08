# NÃO EXECUTAR contra o banco de produção

Estes scripts SQL foram usados em correções manuais pontuais, fora do
controle de versão do Flyway (`backend/src/main/resources/db/migration/`).
Foram movidos para cá pela auditoria de banco de dados de 2026-08 porque:

- Vários fazem a mesma correção de formas divergentes ("SUPREME-DATABASE-*"),
  sem idempotência garantida contra o histórico real do Flyway.
- `setup-database.sql` recria um schema alternativo (com constraints que não
  existem nas migrations reais) — diverge do schema de produção.
- `setup-indexes.sql` referencia tabelas/colunas que não existem no schema
  atual (`pg_students`, `pgs_documents`, `system_telemetry.timestamp`).
- `robust-maintenance.sql` reintroduz colunas (`created_by_name`,
  `created_by_position`) que a migration `V3__Normalization_3FN.sql` removeu
  explicitamente.
- `FIX-ROOT-ACCESS.sql` foi atualizado para não fixar hash de senha em texto
  plano, mas mantém-se aqui por não fazer parte do fluxo de deploy padrão
  (o `DataLoader` já sincroniza o usuário `rootmaster` a partir de
  `APP_ROOT_PASSWORD` no boot).

Qualquer correção de schema necessária deve virar uma nova migration Flyway
numerada em `backend/src/main/resources/db/migration/`, nunca um script solto
rodado manualmente contra o Neon de produção.
