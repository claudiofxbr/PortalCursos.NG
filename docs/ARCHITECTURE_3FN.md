# Arquitetura de Dados PortalCursos.NG (Padrão 3FN & e-MEC)

Este documento descreve a nova estrutura de dados normalizada aplicada ao sistema para garantir integridade, performance no Neon PostgreSQL e conformidade com as exigências do MEC para cursos de Pós-Graduação.

## 1. Normalização 3FN (Terceira Forma Normal)

### Auditoria Centralizada
Todas as entidades principais agora herdam de `BaseAuditEntity`, que utiliza o campo `creator_id` apontando para a tabela `staff_members`.
- **Anteriormente**: Campos como `coordinatorName` ou `reportedBy` eram strings ou IDs soltos em múltiplas tabelas.
- **Agora**: Toda ação é vinculada a um membro do staff real, garantindo rastreabilidade total sem redundância de dados.

### Entidade Course (Conformidade e-MEC)
A entidade `Course` foi totalmente reformulada para refletir os campos exigidos pela plataforma e-MEC, eliminando campos genéricos:
- `codigoIes` e `codigoCurso`: Identificadores únicos oficiais.
- `denominacaoCurso`: Nome oficial do curso.
- `nivelPosGraduacao`: Enum (LATO_SENSU, STRICTO_SENSU).
- `isLocked`: Mecanismo de segurança que impede alteração de campos críticos após a criação do curso sem processo administrativo.

## 2. Otimizações de Banco de Dados (Neon/PostgreSQL)

### Índices Estratégicos
Foram implementados índices para acelerar a auditoria e filtragem:
- `idx_courses_creator`, `idx_students_creator`, `idx_repairs_creator`: Otimizam queries de "Minhas atividades".
- `idx_telemetry_timestamp`: Acelera a visualização de logs de sistema em tempo real.

### Integridade Referencial
O uso de `UUID` para entidades globais (`Course`) e `Long` para entidades locais (`Student`) foi padronizado para equilibrar segurança e performance de indexação.

## 3. Segurança de Dados
- **Anonimização de Erros**: O `GlobalExceptionHandler` intercepta erros de SQL e detalhes técnicos, retornando apenas mensagens amigáveis ao cliente, prevenindo vazamento de informações de infraestrutura.
- **Auditoria Automática**: O `CourseController` e outros injetam automaticamente o membro do staff logado como criador da entidade.

---
*Atualizado em: 13 de Maio de 2026*
*Protocolo: V39.5-NORMALIZATION-COMPLETE*
