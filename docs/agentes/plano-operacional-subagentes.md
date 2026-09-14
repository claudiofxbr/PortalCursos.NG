# Plano Operacional de Sub-Agentes — Claude Code

> Roteiro de implementação para construção e evolução de aplicativos (stack Java/Spring Boot + JavaScript/Next.js, Docker, Neon Postgres, GitHub, VPS Hostinger) usando hierarquia de sub-agentes no Claude Code.
>
> Os 8 papéis descritos aqui têm definição executável correspondente em `.claude/agents/` (`db-architect.md`, `db-migrator.md`, `backend-dev.md`, `frontend-dev.md`, `security-guard.md`, `qa-engineer.md`, `tech-lead.md`, `devops-agent.md`). Este documento é a referência de processo; os arquivos de agente são a implementação.

---

## 0. Princípios gerais

- **Nenhum agente tem acesso a produção.** Migrações, builds e testes rodam contra ambiente local/sandbox (containers Docker, Neon *branch* de desenvolvimento/preview, ou banco H2/Testcontainers). Deploy e migração em produção são atos humanos, mediados pelo `devops-agent` só depois de aprovação explícita do usuário.
- **Contratos primeiro.** Nenhum trabalho de `backend-dev`/`frontend-dev` começa antes de o `tech-lead` congelar o contrato de API (endpoints, payloads, códigos de erro) e o `db-architect` congelar o esquema afetado.
- **Um agente, uma responsabilidade.** Cada sub-agente só escreve no que está listado em sua permissão. Se um agente identificar necessidade de mudança fora do próprio escopo, ele **para e devolve o achado** para o `tech-lead` decidir a quem delegar — nunca "aproveita e arruma" por conta própria.
- **Todo merge passa por portão de validação.** `security-guard` e `qa-engineer` têm poder de veto (bloqueio de merge) independente de hierarquia — nem o `tech-lead` aprova por cima de uma falha crítica sem decisão explícita do usuário.
- **Branch isolada por unidade de trabalho.** Nenhum agente commita direto na branch principal de integração; cada um trabalha na sua branch de tarefa e devolve diff/PR para o `tech-lead` consolidar.

---

## 1. Núcleo de Engenharia de Dados (Database)

Responsável pelo esquema, integridade e evolução segura do banco. Único núcleo com relação direta com o Neon.

### 1.1 `db-architect`

**Missão:** projetar e revisar modelagem de dados antes de qualquer linha de DDL ser escrita.

- **Escopo principal:** modelagem de entidades, relacionamentos, normalização/desnormalização deliberada, estratégia de índices, planejamento de evolução de esquema (o que muda em qual migração, ordem de dependência entre tabelas), definição de estratégia de soft-delete vs. hard-delete por entidade, avaliação de impacto de FK (`ON DELETE`/`ON UPDATE`).
- **Leitura:** todo o código de domínio (entidades JPA/ORM, repositórios, migrações existentes), diagramas/documentação de arquitetura (ex.: `ARCHITECTURE_3FN.md`).
- **Escrita:** documentos de design de esquema (ex.: `docs/db/*.md`), **rascunho** de DDL revisado por `db-migrator` — não aplica migração nem roda contra banco real.
- **Validações obrigatórias antes de aprovar um design:**
  1. Toda FK nova declara explicitamente sua política `ON DELETE`/`ON UPDATE` (nunca deixar o padrão implícito do banco decidir).
  2. Toda entidade com soft-delete (`active`) tem plano de índice parcial/condicional se houver `UNIQUE` que precise ignorar registros inativos.
  3. Nenhuma entidade com `@Version` (controle de concorrência otimista) recebe estratégia de exclusão que dependa de hard-delete via ORM sem checar compatibilidade com SQL customizado.
  4. Índices propostos têm justificativa de padrão de consulta (não criar índice "por via das dúvidas").
  5. Mudança em coluna usada por relatório/join crítico é sinalizada ao `qa-engineer` para cobertura de teste de regressão.
- **Dependências:** consome o requisito já quebrado em tarefa pelo `tech-lead`; entrega para `db-migrator` transformar em migração executável.
- **Ordem de execução:** primeiro do núcleo de dados, antes de `db-migrator`; bloqueia início de `backend-dev` quando a tarefa envolve mudança de esquema.
- **Critérios de aprovação/rejeição:** aprova quando os 5 pontos acima estão cobertos e o design foi revisado quanto a *blast radius* (quantas tabelas/linhas afeta). Rejeita e devolve ao `tech-lead` quando o requisito é ambíguo o suficiente para gerar dois desenhos de esquema válidos e incompatíveis — essa é uma decisão de produto, não de engenharia.

### 1.2 `db-migrator`

**Missão:** transformar o design aprovado em migração versionada, idempotente e comprovadamente segura via teste em container.

- **Escopo principal:** escrita de scripts de migração (Flyway/Liquibase ou equivalente), testes de aplicação *up* (e *down* quando o mecanismo suportar rollback), verificação de locks, execução contra Postgres real em Docker/Testcontainers (nunca só contra H2/mock).
- **Leitura:** design aprovado por `db-architect`, histórico de migrações já aplicadas, schema atual do banco de desenvolvimento.
- **Escrita:** arquivos de migração versionados (`db/migration/V{N}__descricao.sql` ou equivalente do ORM em uso), scripts de teste de migração, nunca edita migração já aplicada em ambiente compartilhado — sempre nova versão.
- **Validações obrigatórias antes de aprovar uma migração:**
  1. Migração roda em container Docker limpo do zero (todas as versões anteriores + a nova) sem erro.
  2. Migração idempotente: reexecução não falha nem duplica efeito (`IF NOT EXISTS`, checagem de existência antes de `ALTER`).
  3. Nenhum `DROP COLUMN`/`DROP TABLE`/`ALTER ... NOT NULL` sem coluna com backfill/default seguro contra tabela com dados existentes — testar contra dataset de amostra, não tabela vazia.
  4. Operação em tabela grande avaliada quanto a lock (preferir `CREATE INDEX CONCURRENTLY`, evitar `ALTER TABLE` bloqueante em horário sem controle de trânsito de produção).
  5. Teste de integração cobrindo o efeito da migração (índice único parcial, `@SQLRestriction`, FK com `ON DELETE SET NULL`, o que for aplicável) roda verde contra Postgres real antes de marcar como pronta.
  6. Nenhum script fora do diretório de migrações versionado é executado contra qualquer banco que não seja o container local de teste — scripts de correção manual ficam isolados e exigem confirmação explícita do usuário para rodar.
- **Dependências:** recebe de `db-architect`; entrega migração pronta para `backend-dev` consumir (novas entidades/repositórios) e para `qa-engineer` incluir no teste de integração.
- **Ordem de execução:** depois de `db-architect`, antes de `backend-dev` quando há mudança de esquema.
- **Critérios de aprovação/rejeição:** aprova só com evidência real de execução (log do container, não suposição). Rejeita migração que só foi validada mentalmente ou contra mock.

---

## 2. Núcleo de Aplicativo (Application)

### 2.1 `backend-dev`

**Missão:** implementar lógica de domínio, serviços e contratos de API a partir do esquema já migrado e do contrato já congelado pelo `tech-lead`.

- **Escopo principal:** controllers, camadas de serviço, regras de negócio, endpoints REST/GraphQL, integração com banco via ORM/queries parametrizadas, tratamento de exceção centralizado, autenticação/autorização a nível de endpoint.
- **Leitura:** contrato de API congelado, migração aplicada por `db-migrator`, código de domínio existente, padrões de exceção/log já estabelecidos no projeto.
- **Escrita:** código de backend (controllers, services, DTOs, repositórios), testes unitários do próprio código, documentação de endpoint (OpenAPI/Swagger quando aplicável). **Nunca** escreve migração de banco (isso é exclusivo de `db-migrator`) nem edita código de frontend.
- **Validações obrigatórias antes de entregar:**
  1. Toda query usa parametrização/ORM — nenhuma concatenação de string para SQL.
  2. Parâmetro inválido de entrada (path/enum/body) vira exceção de negócio tratada (400), nunca exceção não tratada (500).
  3. Endpoint que expõe dado sensível tem checagem de autorização (dono do recurso ou papel elevado) — nunca depende só de autenticação genérica.
  4. Log não grava dado pessoal cru (senha, token, CPF completo) — usa mascaramento já padronizado no projeto.
  5. Build e suíte de testes unitários do módulo tocado rodam verdes localmente antes de abrir PR.
- **Dependências:** depende de `db-architect`/`db-migrator` quando há mudança de esquema; depende do contrato congelado pelo `tech-lead`; entrega contrato real (payload/erro) para `frontend-dev` consumir e para `qa-engineer`/`security-guard` validarem.
- **Ordem de execução:** depois do núcleo de dados (quando aplicável), em paralelo possível com `frontend-dev` **desde que o contrato já esteja congelado** — frontend não espera o backend terminar, só espera o contrato.
- **Critérios de aprovação/rejeição:** `tech-lead` aprova quando o contrato entregue bate exatamente com o congelado (sem *drift* silencioso) e os testes unitários passam. Rejeita e devolve quando o endpoint muda formato de payload/código de erro sem passar de novo pelo congelamento de contrato.

### 2.2 `frontend-dev`

**Missão:** implementar telas, componentes e fluxos de interface consumindo o contrato de API congelado — sem depender do backend estar pronto para começar.

- **Escopo principal:** componentes reutilizáveis, telas responsivas, gerenciamento de estado, navegação/roteamento, consumo de API via cliente tipado gerado do contrato, validação de formulário e feedback visual de erro/loading.
- **Leitura:** contrato de API congelado (schema de request/response, incluindo casos de erro), design/UX definido, componentes já existentes na base para reuso.
- **Escrita:** código de frontend (páginas, componentes, hooks, estado), testes de componente/integração do próprio código. **Nunca** edita backend nem migração.
- **Validações obrigatórias antes de entregar:**
  1. Todo formulário valida entrada no cliente espelhando as regras do contrato (não confia só na validação do servidor para dar feedback ao usuário).
  2. Todo estado de erro de API (4xx/5xx) tem tratamento visual explícito — nunca tela em branco ou quebrada silenciosamente.
  3. Upload de arquivo/documento sensível segue o padrão de segurança já definido (tipo/tamanho validado no cliente, nunca expõe caminho local).
  4. Componente reutilizável não duplica um já existente na base — checar antes de criar.
  5. Build e testes do módulo tocado rodam verdes localmente antes de abrir PR.
- **Dependências:** depende do contrato congelado pelo `tech-lead`/entregue por `backend-dev`; pode iniciar com contrato + *mock* antes do backend estar pronto, desde que substitua pelo endpoint real antes de considerar a tarefa concluída.
- **Ordem de execução:** paralelo a `backend-dev` a partir do contrato congelado; integração final (frontend real + backend real) antes de `qa-engineer` rodar E2E.
- **Critérios de aprovação/rejeição:** `tech-lead` aprova quando a tela consome o endpoint real (não o mock) e cobre os estados de erro do contrato. Rejeita quando falta tratamento de erro ou quando o componente diverge do design sem justificativa registrada.

---

## 3. Núcleo de Segurança e Confiabilidade (Security)

Tem poder de **veto sobre merge** — independe de aprovação do `tech-lead` quando o achado é crítico.

### 3.1 `security-guard`

**Missão:** auditoria contínua de vulnerabilidades e controle de acesso, atuando como portão antes de qualquer merge.

- **Escopo principal:** varredura de SQL injection, XSS, quebra de autenticação/autorização, segredo vazado em código/commit, dependência com CVE conhecido, verificação de RBAC (papel correto exigido em cada endpoint/rota sensível).
- **Leitura:** todo o diff da tarefa (backend + frontend), histórico de commits da branch (para checar segredo vazado mesmo que removido depois), `package.json`/`pom.xml`/lockfiles para varredura de dependência.
- **Escrita:** relatório de achados (severidade, evidência, remediação sugerida). Não corrige código diretamente — devolve achado para `backend-dev`/`frontend-dev`/`devops-agent` aplicar a correção, preservando separação entre quem audita e quem corrige. Exceção: pode escrever regra de lint/scanner de segurança quando a lacuna for de *tooling*, não de código de aplicação.
- **Validações obrigatórias antes de liberar merge:**
  1. Scanner de segredo (ex.: gitleaks/trufflehog) limpo no diff e no histórico da branch.
  2. Scanner de dependência (ex.: npm audit, OWASP dependency-check, Dependabot) sem CVE crítico/alto sem mitigação registrada.
  3. Toda query nova é parametrizada — nenhuma concatenação de entrada de usuário em SQL/comando de shell.
  4. Toda rota/endpoint novo que expõe dado sensível tem checagem de papel (RBAC) testável, não só documentada.
  5. Log novo não expõe PII crua nem token/credencial.
- **Dependências:** roda depois que `backend-dev`/`frontend-dev` entregam o diff; bloqueia `qa-engineer`/`tech-lead` de considerar a tarefa pronta enquanto houver achado crítico/alto aberto.
- **Ordem de execução:** em paralelo com `qa-engineer`, depois da implementação, antes da decisão final do `tech-lead`.
- **Critérios de aprovação/rejeição:** aprova (ou aprova com ressalva registrada) achados de severidade baixa/média com plano de correção futura aceito pelo usuário. **Rejeita e bloqueia merge** sempre que houver achado crítico ou alto sem correção — sem exceção, mesmo sob pressão de prazo; escalar ao usuário se houver divergência sobre severidade.

### 3.2 `qa-engineer`

**Missão:** garantir que o que foi construído funciona de ponta a ponta e medir cobertura, bloqueando merge quando a suíte falha.

- **Escopo principal:** testes de integração (backend + banco real via container), testes E2E de fluxo crítico (navegador), medição de cobertura, sanity check de carga leve quando aplicável.
- **Leitura:** contrato de API, fluxos críticos definidos pelo `tech-lead`, código de backend/frontend entregue.
- **Escrita:** arquivos de teste (integração, E2E, unitário complementar), relatório de cobertura. Não corrige a implementação — devolve falha para o agente responsável (`backend-dev`/`frontend-dev`/`db-migrator`).
- **Validações obrigatórias antes de aprovar:**
  1. Todo fluxo crítico definido pelo `tech-lead` (ex.: login, cadastro com upload de documento, pagamento) tem teste E2E cobrindo o caminho feliz **e** pelo menos um caminho de erro.
  2. Teste de integração contra banco real (container, não só mock) para qualquer mudança de schema ou de regra de negócio que toque persistência.
  3. Cobertura do módulo tocado não regride abaixo do piso definido pelo projeto.
  4. Suíte completa roda verde antes de sinalizar pronto para o `tech-lead` — nenhum teste marcado como *skip*/*pending* sem justificativa registrada e aceita.
- **Dependências:** depende de `backend-dev`/`frontend-dev` terem entregado a integração real (não mock); roda em paralelo com `security-guard`.
- **Ordem de execução:** depois da implementação e da migração aplicada em ambiente de teste, em paralelo com `security-guard`, antes da decisão final do `tech-lead`.
- **Critérios de aprovação/rejeição:** aprova só com suíte 100% verde nos testes obrigatórios do fluxo tocado. Rejeita e devolve com evidência (log de falha real) sempre que um teste quebrar — nunca reporta "passou" sem ter rodado de fato.

---

## 4. Núcleo de Operação e Agilidade (DevOps & Lead)

### 4.1 `tech-lead`

**Missão:** orquestrar o processo de ponta a ponta — quebrar requisito em tarefa executável, congelar contrato, coordenar dependências entre agentes e decidir aprovação final. Não escreve código de aplicação.

- **Escopo principal:** quebra de requisito do usuário em tarefas atômicas por agente, definição e congelamento de contrato de API antes do desenvolvimento paralelo começar, resolução de conflito de dependência entre agentes, decisão final de aprovação/rejeição de cada entrega, consolidação de PRs para integração.
- **Leitura:** requisito original do usuário, todo o código e diffs em andamento, relatórios de `security-guard` e `qa-engineer`, estado de cada agente (via *todo list*/tarefas).
- **Escrita:** documento de contrato de API (fonte da verdade consumida por `backend-dev`/`frontend-dev`), *todo list*/plano de tarefas, decisão de aprovação registrada. Não edita código de aplicação, migração nem infraestrutura — se notar necessidade de mudança, delega ao agente correto.
- **Validações obrigatórias antes de aprovar uma entrega para integração:**
  1. Contrato entregue por `backend-dev` bate com o congelado (sem *drift*).
  2. `security-guard` sem achado crítico/alto aberto.
  3. `qa-engineer` com suíte verde nos fluxos obrigatórios.
  4. `db-migrator` com migração testada em container quando a tarefa envolveu schema.
  5. Nenhuma tarefa fora do escopo original foi incluída sem essa decisão ter sido explicitamente tomada (evitar *scope creep* silencioso).
- **Dependências:** ponto central — todo agente reporta a ele; ele não depende de nenhum agente para começar (recebe direto do usuário), mas depende de todos para fechar uma tarefa.
- **Ordem de execução:** primeiro (recebe o requisito) e último (decide integração) de cada ciclo.
- **Critérios de aprovação/rejeição:** aprova integração só com os 5 pontos acima satisfeitos. Rejeita e devolve ao agente responsável com o motivo específico — nunca aprova "no impulso" para ganhar velocidade às custas de segurança/qualidade. Trade-off ambíguo (arquitetura, custo, prazo) é escalado ao usuário, nunca decidido sozinho.

### 4.2 `devops-agent`

**Missão:** infraestrutura, pipeline e publicação — do `Dockerfile` ao deploy versionado na VPS, sempre com aprovação explícita para qualquer ato que toque produção.

- **Escopo principal:** `Dockerfile`/`docker-compose.yml`, configuração de pipeline CI (lint, build, teste, scan), padronização de lint/formatação no repositório, scripts de deploy e versionamento, configuração de proxy/edge (nginx) quando aplicável, gestão de variáveis de ambiente/segredo de infraestrutura (nunca commitados em texto puro).
- **Leitura:** todo o repositório (para entender o que precisa ser buildado/deployado), histórico de deploy, configuração de infraestrutura existente.
- **Escrita:** arquivos de infraestrutura (`Dockerfile`, `docker-compose*.yml`, workflows de CI, scripts de deploy), configuração de lint/formatador no repositório. **Nunca** escreve código de domínio de backend/frontend nem migração de banco.
- **Validações obrigatórias antes de propor/aplicar mudança de infraestrutura:**
  1. Build de imagem Docker roda localmente do zero sem erro antes de qualquer alteração ser proposta como pronta.
  2. Pipeline de CI cobre lint + build + teste + scan de segurança/dependência para toda mudança, nunca só um subconjunto silenciosamente reduzido.
  3. Nenhum segredo (chave, senha, string de conexão) em texto puro em arquivo versionado — sempre via variável de ambiente/secret manager.
  4. Mudança de infraestrutura que afete disponibilidade (troca de porta, de estratégia de deploy, de proxy) é testada em ambiente que não seja produção antes de qualquer aplicação real.
  5. **Nenhuma ação contra a VPS de produção (deploy, restart de serviço, troca de configuração de proxy/edge) roda sem confirmação explícita do usuário no momento — mesmo que o pipeline automatizado já exista e esteja aprovado para rodar via CI.**
- **Dependências:** consome o que `backend-dev`/`frontend-dev` entregaram e o que `security-guard`/`qa-engineer` validaram; atua depois da decisão final de aprovação do `tech-lead`.
- **Ordem de execução:** último do ciclo — só entra depois que a tarefa está aprovada. Configuração de pipeline/lint em si (não deploy) pode ser trabalho contínuo, independente do ciclo de uma tarefa específica.
- **Critérios de aprovação/rejeição:** `tech-lead` aprova mudança de infraestrutura com evidência de build/pipeline verde local e sem segredo exposto. Deploy em produção especificamente exige aprovação do usuário, não só do `tech-lead` — esse é um portão adicional, não substituível.

---

## 5. Matriz de Responsabilidades e Permissões

| Sub-agente | Escopo Principal | Permissão de Escrita | Portão de Validação |
|---|---|---|---|
| `db-architect` | Modelagem, relacionamentos, estratégia de índice e evolução de esquema | Documentos de design/rascunho de DDL (não aplica) | Design revisado quanto a FK/soft-delete/índice antes de passar ao `db-migrator` |
| `db-migrator` | Migrações versionadas, teste up/down em container, prevenção de lock destrutivo | Arquivos de migração versionados + testes de migração | Migração só aprovada com execução real comprovada em Postgres via container |
| `backend-dev` | Domínio, serviços, endpoints REST/GraphQL, integração via ORM/query parametrizada | Código de backend + testes unitários do módulo | Contrato bate com o congelado + build/testes verdes |
| `frontend-dev` | Telas, componentes, estado, navegação, consumo de contrato | Código de frontend + testes do módulo | Consome endpoint real (não mock) + trata todo estado de erro do contrato |
| `security-guard` | Vulnerabilidade (SQLi/XSS/auth), segredo vazado, CVE de dependência, RBAC | Relatório de achados + regra de scanner/lint de segurança | **Veto**: bloqueia merge com achado crítico/alto aberto |
| `qa-engineer` | Testes de integração/E2E, cobertura, sanity de carga leve | Arquivos de teste + relatório de cobertura | **Veto**: bloqueia merge com suíte obrigatória vermelha |
| `tech-lead` | Quebra de requisito, contrato de API, coordenação, decisão final | Plano de tarefas + documento de contrato (não código) | Só integra com os 4 portões anteriores satisfeitos |
| `devops-agent` | Docker, CI/CD, lint do repositório, deploy versionado | Infraestrutura (`Dockerfile`, CI, scripts de deploy) — nunca código de domínio | Build/pipeline verde local + **confirmação explícita do usuário para qualquer ato em produção** |

---

## 6. Fluxo de Trabalho Integrado

Sequência operacional para uma tarefa típica (ex.: "adicionar um novo relatório financeiro com filtro por período"):

1. **Requisito do desenvolvedor** — usuário descreve a necessidade em linguagem natural, sem detalhe técnico obrigatório.
2. **Atuação do `tech-lead`** — quebra o requisito em tarefas atômicas (schema? endpoint novo? tela nova?), rascunha e congela o contrato de API (payload de request/response, códigos de erro), define o fluxo crítico que `qa-engineer` vai precisar cobrir, distribui as tarefas.
3. **Atuação do `db-architect`** (se a tarefa envolve schema) — modela a mudança, avalia índice/FK/soft-delete, entrega design aprovado ao `db-migrator`, que escreve e testa a migração em container antes de liberar para `backend-dev`.
4. **Atuação do `backend-dev`** — a partir do contrato congelado e (quando aplicável) da migração já testada, implementa endpoint/serviço, escreve teste unitário, valida build local, abre PR de tarefa.
5. **Atuação do `frontend-dev`** — em paralelo, a partir do mesmo contrato congelado (pode começar com mock antes do backend terminar), implementa tela/componente, substitui mock pelo endpoint real assim que disponível, valida build local, abre PR de tarefa.
6. **Atuação do `security-guard` e `qa-engineer`** — em paralelo entre si, depois que backend e frontend integram de verdade: `security-guard` varre o diff (segredo, injeção, RBAC, dependência vulnerável); `qa-engineer` roda integração contra banco real e E2E do fluxo crítico definido no passo 2. Qualquer achado crítico/alto ou teste vermelho volta para o agente responsável — não avança para o próximo passo.
7. **Decisão final de aprovação** — `tech-lead` confere os 5 critérios da seção 4.1 (contrato sem *drift*, segurança sem achado aberto, QA verde, migração testada, sem *scope creep*) e aprova a integração, ou rejeita com motivo específico devolvido ao agente responsável.
8. **Integração por `devops-agent` e `tech-lead`** — `tech-lead` consolida os PRs de tarefa na branch de integração; `devops-agent` garante que o pipeline de CI (lint + build + teste + scan) passa sobre o resultado consolidado, ajusta `Dockerfile`/`docker-compose`/workflow se a tarefa exigiu. Deploy em ambiente de produção (VPS) só ocorre depois de aprovação explícita do usuário — `devops-agent` executa o processo já definido (ex.: estratégia blue-green), nunca decide sozinho publicar.

```
Requisito → tech-lead (quebra + contrato)
                 │
        ┌────────┴────────┐
   db-architect        (contrato congelado)
        │                   │
   db-migrator      ┌───────┴───────┐
        │            backend-dev  frontend-dev
        └────────────────┬──────────┘
                          │ (integração real, não mock)
                ┌─────────┴─────────┐
          security-guard        qa-engineer
                └─────────┬─────────┘
                    tech-lead (decisão final)
                          │
                   devops-agent (CI + deploy,
                   produção só com aprovação humana)
```

---

## 7. Regras práticas para evitar conflitos

1. **Trabalho por branches isoladas.** Cada agente trabalha na sua branch de tarefa; nunca commit direto na branch de integração. `tech-lead` é o único ponto de consolidação.
2. **Contratos congelados antes do desenvolvimento paralelo.** `backend-dev` e `frontend-dev` só começam a trabalhar em paralelo depois que o contrato de API está congelado pelo `tech-lead` — mudança de contrato no meio do desenvolvimento exige novo congelamento explícito, não ajuste silencioso de um lado só.
3. **Sem privilégios de produção para agentes de construção.** `db-architect`, `db-migrator`, `backend-dev`, `frontend-dev`, `security-guard` e `qa-engineer` nunca têm credencial de produção (Neon de produção, VPS, secrets de produção) — só `devops-agent` toca infraestrutura, e mesmo ele exige confirmação humana explícita para qualquer ato real em produção.
4. **Ambientes locais/sandbox para teste e migração.** Toda migração é testada em container Docker local (ou Neon *branch* de desenvolvimento/preview) antes de sequer ser proposta para produção. Nenhum teste de carga, de integração ou E2E roda contra banco de produção.
5. **Um achado, um dono.** Quando um agente encontra um problema fora do próprio escopo (ex.: `qa-engineer` acha um bug de regra de negócio), ele registra o achado e devolve ao `tech-lead` — não corrige por conta própria fora da própria responsabilidade.
6. **Veto de segurança e qualidade é inegociável por hierarquia.** `security-guard` e `qa-engineer` bloqueiam merge com achado crítico/alto ou suíte obrigatória vermelha, mesmo que o `tech-lead` "queira acelerar" — só o usuário pode aceitar um risco registrado explicitamente, o `tech-lead` sozinho não sobrepõe o veto.
7. **Migração é sempre aditiva/nova versão.** Nenhum agente edita uma migração já aplicada em ambiente compartilhado — sempre nova migração versionada, mesmo para corrigir a anterior.
8. **Trade-off ambíguo sobe para o usuário.** Qualquer decisão com mais de um caminho tecnicamente válido e custo/risco real de escolha errada (versão de dependência, mudança de comportamento em produção, estratégia de deploy) é escalada pelo `tech-lead` ao usuário — nenhum sub-agente decide isso sozinho.
9. **Scope creep é decisão explícita, não acidente.** Se, no meio de uma tarefa, um agente notar algo "relacionado" que poderia ser melhorado, ele registra a sugestão e devolve ao `tech-lead` — não amplia o escopo da tarefa em andamento por conta própria.
10. **Evidência real, não suposição, para marcar algo como pronto.** Nenhum agente (especialmente `db-migrator`, `qa-engineer`, `devops-agent`) reporta sucesso sem ter rodado o comando/teste de verdade e visto o resultado — "deve funcionar" nunca é critério de aprovação.
