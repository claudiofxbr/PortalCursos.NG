# PortalCursos.NG - Visão Geral da Arquitetura "Omega"

Esta documentação descreve a arquitetura do sistema após a estabilização e unificação da base de estudantes.

## 1. Arquitetura de Dados (JPA Single Table Inheritance)

Para garantir a integridade dos dados e facilitar consultas globais (como verificação de unicidade de CPF e Email), o sistema agora utiliza uma única tabela para todos os tipos de estudantes.

- **Tabela**: `students`
- **Estratégia**: `InheritanceType.SINGLE_TABLE`
- **Coluna Discriminadora**: `student_type`
  - `GRAD`: Alunos de Graduação (Entidade `Student`)
  - `POSTGRAD`: Alunos de Pós-Graduação (Entidade `PostgradStudent`)

### Benefícios
- **Integridade Global**: Verificação de duplicidade de CPF e Email em toda a base com uma única query.
- **Relacionamentos Simplificados**: Pagamentos, documentos e sessões apontam para uma única tabela base.
- **Performance**: Redução de JOINs complexos para listagens gerais.

## 2. Camada de Negócio (Services)

Os serviços foram refatorados para garantir a consistência dos dados de entrada.

### Sanitização de Dados
Todos os números de CPF e Telefone são automaticamente sanitizados (`replaceAll("[^0-9]", "")`) antes da persistência. Isso evita inconsistências como registros duplicados devido a máscaras diferentes (ex: `123.456.789-00` vs `12345678900`).

### Unicidade Global
As verificações de unicidade agora utilizam métodos `existsByEmailGlobal` e `existsByCpfGlobal` que ignoram filtros de exclusão lógica (`@Where(clause = "active=true")`) para garantir que um CPF não seja reutilizado mesmo que o registro anterior esteja inativo, a menos que explicitamente permitido.

## 3. Armazenamento de Arquivos

Utiliza-se o `StorageService` para gerenciar uploads.
- **Estrutura de Pastas**:
  - `fotos-perfil/`: Fotos de alunos de graduação.
  - `postgrad/`: Estrutura organizada para alunos de pós-graduação.
  - `uploads/`: Pasta base configurada no Nginx para acesso público seguro.

## 4. Segurança

- **JWT**: Autenticação via tokens JWT.
- **RBAC**: Controle de acesso baseado em Roles (`ADMIN`, `SECRETARIA`, `MATRICULA`, etc.) configurado no `WebSecurityConfig`.
- **CORS**: Configurado dinamicamente para permitir origens específicas de produção e desenvolvimento.

## 5. Infraestrutura de Produção

- **Nginx**: Atua como Proxy Reverso encaminhando o tráfego para:
  - Frontend (Next.js): Porta `3000`
  - Backend (Spring Boot): Porta `8080`
- **PM2**: Gerencia os processos Node.js e Java para alta disponibilidade.
- **Banco de Dados**: PostgreSQL hospedado no Neon.tech.
