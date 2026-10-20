# Exportação para o PortalCursos.NG — Pix, cartão de crédito e cartão de débito

Procedimento para ativar os três meios de pagamento no PortalCursos.NG, com base no que já funciona no CVFacil.NG (PagBank). Este documento **não altera nada** no PortalCursos.NG: cada mudança abaixo só é executada com autorização expressa do usuário (Regras Críticas 1 a 3).

Elaborado pelo agente `portal-pagamentos-procedimentos`, sob supervisão do `portal-pagamentos-gerente` e com implementação a cargo do `portal-solucao-robusta`.

## 1. Situação de partida

| Item | CVFacil.NG (referência) | PortalCursos.NG (hoje) |
|---|---|---|
| Stack | Next.js + Neon (SQL direto) | Spring Boot + JPA + Flyway |
| Meios | Crédito, débito e Pix | Pix (real) e boleto (simulado) |
| Integração PagBank | Checkout hospedado (`POST /checkouts`) | Orders API direta (`POST /orders`, cobrança `PIX`) |
| Unidade cobrada | Pedido por plano (`orders`) | Fatura por aluno (`payments`, coluna `psp_order_id`) |
| Webhook | `/api/payments/pagbank/webhook`, reconsulta a API | `/api/finance/pix/webhook/{paymentId}/{token}`, reconsulta a API |
| Liberação | `releaseIfConfirmed` (ponto único) | `PixWebhookController` marca `PAID` |

Arquivos de referência: CVFacil.NG `lib/pagbank.ts`, `lib/paymentRelease.ts`, `lib/orders.ts`, `app/api/payments/*`. PortalCursos.NG `PagBankPixGatewayService`, `PixWebhookController`, `PaymentCodeGenerationService`, `EPaymentMethod`, `Payment`, migração V23, `frontend/app/finance/page.tsx`.

## 2. Três modelos de processo avaliados

| Modelo | Descrição | Prós | Contras |
|---|---|---|---|
| **A. Checkout hospedado para tudo** | Portar o fluxo do CVFacil.NG: um checkout com `CREDIT_CARD`, `DEBIT_CARD` e `PIX`; o cliente paga na página do PagBank | Menor escopo de segurança (sem dados de cartão no app), já validado no sandbox (crédito e Pix) | Troca o Pix atual (QR dentro do app) por redirecionamento; mexe no que já funciona |
| **B. Orders API direta para tudo** | Cobranças `CREDIT_CARD` e `DEBIT_CARD` pela API, com cartão criptografado no navegador (SDK do PagBank) | Experiência 100% dentro do app | Maior risco e esforço (escopo PCI maior, autenticação 3DS no débito, tratamento de recusas); nada validado ainda |
| **C. Híbrido (recomendado)** | Manter o Pix atual intacto; adicionar crédito e débito pelo Checkout hospedado (`payment_methods`: só os dois cartões) | Não toca no Pix que funciona (Regra 1), reaproveita o que foi validado no CVFacil.NG, cartão fora do escopo do app | Dois caminhos de webhook (Pix por `ORDE_`, cartão por `CHEC_`) |

**Regra de troca de modelo:** começar pelo C. Só trocar para A ou B se o C falhar de forma sem correção segura, e a troca só vale quando o novo modelo estiver correto, validado e sem erros; até lá o anterior permanece como base.

## 3. Passos que dependem do usuário (não podem ser delegados nem simulados)

1. **Homologação PagBank** da conta do PortalCursos.NG (e token de sandbox).
2. **Débito exige aprovação interna do PagBank** ("O método de pagamento débito requer aprovação interna prévia"): solicitar no chamado de suporte e aguardar. Sem isso, o cartão de débito não aparece, mesmo com o código pronto. Crédito e Pix foram confirmados no sandbox do CVFacil.NG; débito ainda está pendente lá também (chamado 1452488350).
3. **Chave Pix** cadastrada na conta PagBank de produção.
4. **Segredos** só por GitHub Secrets / `.env` da VPS, definidos com `gh secret set` (campo oculto), nunca em chat, código ou log: `PAGSEGURO_API_TOKEN`, `PAGSEGURO_API_BASE_URL`, `PAGSEGURO_WEBHOOK_SECRET`.
5. **URL pública do webhook** cadastrada/aceita pelo PagBank (https).
6. Mudar para produção (`api.pagseguro.com`) somente depois da homologação aprovada e de teste completo no sandbox.

## 4. Procedimento de implantação (Modelo C)

Cada etapa só começa depois que a anterior passou no portão de qualidade (seção 6).

### Etapa 0 — Configuração do ambiente (pré-requisito, achado da análise de compatibilidade)
- `devops/docker-compose.prod.yml` (bloco `x-backend`) **não repassa** ao contêiner `PAGSEGURO_API_TOKEN`, `PAGSEGURO_API_BASE_URL`, `PAGSEGURO_WEBHOOK_SECRET` nem `PORTALCURSOS_PUBLIC_API_URL`. Sem eles o Pix em produção responde "Gateway de pagamento não configurado" e o webhook apontaria para `localhost:8080`. Incluir as quatro variáveis (sem valor padrão real) e conferir o `.env` da VPS (suposição ainda não verificada).
- `pagseguro.api.base-url` tem como padrão a **produção** (`https://api.pagseguro.com`). Em sandbox, definir explicitamente `https://sandbox.api.pagseguro.com`; token de sandbox não funciona em produção e vice-versa.
- `PORTALCURSOS_PUBLIC_API_URL` deve ser a URL pública https da API (ex.: `https://xavierbr-vps.tech`), usada na URL do webhook e no `redirect_url`.
- **Trava de ambiente:** fora de produção o cartão de teste do PagBank é público. Introduzir `PAGBANK_ENV` (`sandbox` por padrão; produção só com o valor exato `production`) e, fora de produção, permitir o pagamento por cartão apenas a administradores/testadores listados em variável própria (padrão do CVFacil.NG: `SANDBOX_TESTER_EMAILS`), para ninguém baixar fatura real com cartão de teste.

### Etapa 1 — Modelo de dados
- Incluir `CREDIT_CARD` e `DEBIT_CARD` em `EPaymentMethod`. Conferido: `payments.method` é `VARCHAR(20)` sem `CHECK` (V1); os nomes cabem.
- Migração Flyway **aditiva** nova (a próxima é a **V24**, pois a última é a V23): coluna `charged_amount_cents` (valor em centavos **fixado no momento da criação do checkout**) e, se desejado, `psp_checkout_id`. O `psp_order_id` (VARCHAR(64)) comporta um id `CHEC_` (cerca de 41 caracteres), mas misturar `ORDE_` e `CHEC_` na mesma coluna obriga o webhook a decidir pelo prefixo; preferir coluna separada para clareza.
- **Valor com juros:** `Payment.getTotalAmount()` muda a cada dia em fatura `OVERDUE` (1,8% a.m. pro-rata) e `updateCharge` pode alterar o valor. A baixa compara o valor pago com `charged_amount_cents` (o valor que o aluno de fato viu no checkout), nunca com o total recalculado no dia do webhook.
- Teste: migrações reais contra Postgres via Testcontainers (`PostgresMigrationIntegrationTest`).

### Etapa 2 — Cliente do Checkout hospedado
- Novo serviço (`PagBankCheckoutGatewayService`), sem mexer em `PagBankPixGatewayService`.
- `POST {baseUrl}/checkouts` com: `reference_id` (`PORTAL-<paymentId>`), `items` (valor em **centavos inteiros, vindo da fatura no servidor**), `payment_methods: [{"type":"CREDIT_CARD"},{"type":"DEBIT_CARD"}]` (objetos, **não** lista de textos: texto simples dá HTTP 400), `redirect_url`/`return_url`, `payment_notification_urls` e `notification_urls` com o mesmo webhook.
- Omitir `customer` é aceito (confirmado no sandbox do CVFacil.NG); o cliente preenche na página do PagBank.
- Ler o link com `rel = "PAY"`; aceitar somente `https` sem usuário/senha.
- Timeout de 10 s, token nunca em log, corpo de erro do PSP nunca em log nem na resposta ao cliente.

### Etapa 3 — Endpoint de criação
- Novo endpoint autenticado (ex.: `POST /api/finance/invoices/{id}/checkout`), com a mesma checagem de dono da fatura (`ownsPayment`) usada em Pix/boleto.
- Recusar fatura já paga; reaproveitar checkout pendente recente da mesma fatura (o link PAY expira em ~2 h) em vez de criar outro.
- Limite de tentativas por usuário (o CVFacil.NG usa 5 a cada 10 min).
- Resposta: apenas `{url}`. **Nunca devolver "aprovado"**: a baixa só ocorre pelo webhook.

### Etapa 4 — Webhook e liberação
- **Achado crítico:** o `PixWebhookController` atual chama `GET /orders/{psp_order_id}`; com um id `CHEC_` essa consulta falha, a exceção é engolida e a resposta é 200, ou seja, **um cartão pago nunca baixaria a fatura e ninguém seria avisado**. O tratamento por prefixo abaixo é obrigatório e não pode ser omitido.
- Preferência: **novo** endpoint de notificação para cartão (ex.: `/api/finance/card/webhook/{paymentId}/{token}`, com o mesmo HMAC e o mesmo `permitAll` restrito a esse caminho), deixando o webhook do Pix intocado (Regra 1). Ele **nunca confia no corpo**: extrai só o id (`ORDE_` ou `CHEC_`) e reconsulta a API.
- Falha temporária na reconsulta (PSP fora do ar, 5xx, timeout) deve devolver **503** (o PagBank reenvia), e não 200 silencioso; só ruído/ignorável devolve 200.
- `CHEC_`: `GET /checkouts/{id}` devolve `reference_id` e `orders[]` (o checkout continua `ACTIVE` mesmo pago e não traz `charges`); consultar cada `ORDE_` (máx. 5), considerar só os que têm o mesmo `reference_id` e somar `charges[].amount.value` com `status = PAID`.
- Baixar a fatura **somente** se: status `PAID` confirmado pela API **e** `reference_id` igual ao da fatura **e** valor pago em centavos **exatamente** igual a `charged_amount_cents` (valor fixado na criação do checkout).
- Expiração: o link `PAY` vale cerca de 2 h (o Pix atual vale até vencimento + 1 dia). Reaproveitar checkout pendente por no máximo ~90 min; depois disso gerar outro (e o valor fixado é recalculado nessa hora).
- Registro de eventos: **implementado sem tabela** (desvio consciente). A baixa já é idempotente por status (`@Version` resolve concorrência) e o log registra o `paymentId`; uma tabela como o `webhook_events` do CVFacil.NG pode virar migração futura se a auditoria exigir.
- O cabeçalho `x-authenticity-token` não é necessário neste desenho: a URL já carrega o HMAC por fatura e a barreira real é a reconsulta autenticada (no sandbox do CVFacil.NG o cabeçalho não casou com a fórmula documentada).
- Baixa idempotente (UPDATE condicional `PENDING → PAID`); notificação repetida é inofensiva. Registrar o método efetivamente usado (crédito/débito).
- Limite por IP no webhook: **não implementado** (desvio consciente). O token HMAC por fatura já barra quem não o conhece e um limite em memória só acrescentaria complexidade.
- Estado implementado: `CardWebhookController` ignora o corpo e reconsulta o checkout gravado na fatura; `CardPaymentReleaseService` é o único ponto de baixa; fatura cancelada que recebe pagamento vai para revisão manual (log), sem baixa automática.

### Etapa 5 — Reconciliação do Pix atual (melhoria opcional)
O webhook do Pix de hoje só verifica `status == PAID`. Recomenda-se, **com autorização do usuário**, passar a conferir também `reference_id` e valor, como no CVFacil.NG. Como o Pix funciona, não tocar sem pedido expresso.

### Etapa 6 — Frontend
- Em `frontend/app/finance/page.tsx`, **um** botão "Cartão" (crédito e débito levam ao mesmo checkout hospedado; o PagBank mostra o meio disponível), preservando Pix e Boleto. O botão fica oculto até `NEXT_PUBLIC_CARD_PAYMENTS_ENABLED=true` no build (variável a ser passada no `deploy.yml`/`docker build`). Implementado na Etapa 6.
- Página de retorno que consulta o estado da fatura no servidor (não confia em parâmetro da URL) e informa "aguardando confirmação" até a baixa.
- Débito: o back-end só o oferece com `PAGBANK_DEBIT_ENABLED=true` (padrão `false`), para não prometer o que ainda não funciona.
- Limitação existente: a listagem de faturas é restrita a perfis elevados; o aluno comum não vê faturas por essa tela nem o histórico usado para confirmar o retorno. Decisão pendente do usuário.
- Landing pages **não** são alteradas.

### Etapa 7 — Configuração e deploy
- `application.properties`: reaproveitar `pagseguro.*`; adicionar apenas o necessário (URL pública do app para `redirect_url`, `PAGBANK_ENV`, lista de testadores de sandbox).
- Variáveis na VPS via `.env` alimentado pelos secrets; sem valores em arquivo versionado (ver Etapa 0 para o compose).
- Deploy pelo pipeline existente (`deploy_ci.sh`, blue-green). Nenhum push/deploy sem autorização.

### Etapa 8 — Cadastro de procedimentos cobrados (CRUD)
Ver a seção 10. Pode ser feita em paralelo às etapas 1 a 7, pois não depende do PagBank, mas a cobrança de um procedimento por Pix/cartão só passa a valer depois das etapas 1 a 6.

## 5. Mapa de status e erros

| Situação no PagBank | Efeito na fatura |
|---|---|
| `PAID` + referência e valor conferem | `PAID` |
| `WAITING`/sem `charges` | continua pendente |
| `DECLINED`/cancelado/expirado | continua pendente; usuário pode gerar novo checkout |
| Referência ou valor divergente | não baixa; registra alerta (sem dado sensível) |
| PSP indisponível, timeout ou 401/403 | erro claro ao usuário (503), nada é baixado |

## 6. Plano de testes (portão de qualidade de cada etapa)

1. **Unitário:** montagem do corpo do checkout (centavos, objetos `{type}`), extração do link PAY, soma de cobranças `PAID`, regra de baixa, idempotência.
2. **Integração com banco real** (Testcontainers/Postgres): migração aditiva, baixa atômica, soft-delete da fatura sem erro. Mock não executa SQL e não substitui isto.
3. **Contrato do PagBank no sandbox:** crédito e Pix aprovados com os dados de teste oficiais; débito somente após a aprovação do PagBank.
4. **Segurança:** webhook sem token/assinatura não libera nada; corpo forjado com valor diferente não baixa; fatura de outro aluno não é acessível; varredura de segredos no diff; dependências sem vulnerabilidade conhecida.
5. **Carga leve / estresse:** rajada de notificações repetidas do mesmo evento (uma baixa só) e do endpoint de criação (limite de tentativas).
6. **Regressão:** Pix existente, boleto simulado, login, matrícula e relatórios continuam iguais.
7. **Fim a fim:** criar fatura → abrir checkout → pagar no sandbox → webhook → fatura `PAID` → tela atualizada.

## 7. Critérios para encerrar
Todos os testes verdes; sem regressão; health `UP` com migrações OK; fluxo sandbox completo para Pix e crédito (e débito se aprovado); revisão final sem achados abertos. Produção só com homologação aprovada, token de produção definido pelo usuário e confirmação expressa.

## 8. Reversão
As mudanças são aditivas: desligar os botões de cartão (flag) restaura o comportamento anterior sem perder dados; a migração não remove nada. Se houver problema após o deploy, reverter o commit e manter o Pix intacto.

## 9. Pendências conhecidas
- Aprovação do cartão de débito pelo PagBank (chamado 1452488350, no CVFacil.NG; solicitar o mesmo para a conta do PortalCursos.NG).
- O formato exato de algumas respostas do débito só poderá ser confirmado depois da aprovação ("VALIDAR NO SANDBOX").
- Confirmar o `.env` real da VPS (se os secrets do PagBank já existem) e se o Pix já foi usado de verdade.
- Confirmar no sandbox do PortalCursos.NG que a notificação do checkout chega com id `CHEC_` (premissa tomada do CVFacil.NG).
- Pix atual: o webhook só confere `PAID`; conferir também referência e valor é melhoria opcional, só com autorização expressa (Regra 1).
- Boleto segue simulado e fora de escopo.

## 10. Cadastro de procedimentos cobrados (CRUD)

Registro, por categoria, de todos os procedimentos que podem ser cobrados dos alunos. Conteúdo fiel ao texto original, sem acréscimos.

### 10.1 Modelo de dados (migração aditiva, após a V24)

Tabelas `procedure_categories` e `billable_procedures` (nomes sugeridos; conferir convenção local):

| Campo | Tipo | Observação |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `category_id` | FK para `procedure_categories` | obrigatório, `ON DELETE RESTRICT` |
| `name` | VARCHAR(200) | nome do procedimento, obrigatório |
| `description` | TEXT | descrição, obrigatória |
| `required_documents` | TEXT | documentos exigidos |
| `purpose` | TEXT | finalidade |
| `operational_note` | TEXT | observação operacional |
| `academic_scope` | VARCHAR(20) | `ALL`, `GRADUATION` ou `POSTGRAD` (a categoria 4 é só da pós) |
| `active` | BOOLEAN | exclusão lógica |
| auditoria | herda `BaseAuditEntity` | criado/alterado por e quando |

`UNIQUE (category_id, name)` parcial em `active = true`. Aqui segue o padrão do projeto: exclusão lógica (`setActive(false)` + `save()`), **nunca** `delete()`/`deleteById()` do Spring Data em entidade com `@Version` (ver CLAUDE.md). Valor cobrado do procedimento: campo opcional `default_amount` (`NUMERIC(12,2)`) para alimentar a cobrança manual existente; se não for pedido, não incluir.

Relação com o que já existe: `Payment` já tem `ESecretaryProcessType` (TRANSCRIPT, DIPLOMA_COPY, CERTIFICATE, ENROLLMENT_CANCELLATION, COURSE_CHANGE, OTHER) e `EPaymentCategory`. O cadastro novo **não os substitui**; a cobrança pode referenciar o procedimento cadastrado (coluna opcional `procedure_id` em `payments`) sem alterar os dados antigos.

### 10.2 API REST

Todas sob `/api/finance/procedures` (ou `/api/v1/procedures`), com `@PreAuthorize` de secretaria/administração para escrita e leitura autenticada para consulta:

| Operação | Rota | Resultado |
|---|---|---|
| Listar | `GET /procedures?category=&q=&page=` | paginada, só ativos |
| Visualizar | `GET /procedures/{id}` | 404 se inexistente/inativo |
| Criar | `POST /procedures` | 201; validação (`@Valid`) de categoria, nome, descrição |
| Atualizar | `PUT /procedures/{id}` | 200; controle de concorrência por `@Version` |
| Eliminar | `DELETE /procedures/{id}` | exclusão lógica; 204 |
| Categorias | `GET /procedure-categories` | as 5 categorias, ordenadas |

Erros pelo padrão central (`GlobalExceptionHandler`, `BusinessException` para entrada inválida, 404 por `ResourceNotFoundException`). Sem PII em log.

### 10.3 Conteúdo inicial (carga por migração Flyway, idempotente)

**1. Ingresso e Matrícula** — primeiro contato formal do discente com a secretaria e formalização do vínculo institucional.
- Entrega de documentação civil e escolar: RG, CPF, certidão de nascimento ou casamento, quitação militar e título de eleitor.
- Comprovação de escolaridade anterior: certificado e histórico do Ensino Médio para a graduação; diploma e histórico de graduação válidos para a pós-graduação lato sensu ou stricto sensu.
- Assinatura do contrato e aceite regimental: contrato de prestação de serviços educacionais em instituições privadas e termo de ciência do regulamento institucional.

**2. Renovação e Ajustes Curriculares** — acompanhamento do avanço da grade curricular a cada período letivo.
- Rematrícula periódica: renovação do vínculo e inscrição nas turmas do semestre ou módulo.
- Ajuste de matrícula: pedidos de acréscimo, cancelamento ou trancamento de disciplinas dentro dos prazos do calendário acadêmico.
- Aproveitamento de estudos e equivalências: solicitação formal com apresentação de ementas e históricos de outras instituições para dispensa de matérias.
- Trancamento ou cancelamento de curso: protocolo de interrupção temporária de estudos ou desvinculação definitiva.

**3. Acompanhamento da Vida Escolar** — procedimentos operacionais e legais que o aluno deve cumprir e comprovar durante o curso.
- Atualização cadastral contínua: manutenção de endereço residencial, e-mail e dados de contato atualizados no sistema acadêmico.
- Justificativas e regimes especiais: pedidos de abono de faltas, amparo por licença-maternidade ou exercícios domiciliares mediante laudo médico nos prazos regulamentares.
- Protocolos de segunda chamada: requerimento para realização de avaliações perdidas dentro das regras regimentais.
- Atividades complementares: envio de certificados, declarações de extensão e eventos para cômputo da carga horária obrigatória da graduação.

**4. Exigências Específicas da Pós-Graduação** — fluxos particulares das formações lato sensu e stricto sensu (`academic_scope = POSTGRAD`).
- Registro de orientação: homologação de orientadores e coorientadores acadêmicos.
- Exame de qualificação: inscrição no exame, submissão prévia de relatórios e documentação para agendamento da banca.
- Composição de bancas: entrega da versão prévia de dissertação ou tese e dados dos membros avaliadores convidados.
- Comprovação de proficiência em língua estrangeira: entrega de certificados aceitos pelo edital ou programa.

**5. Conclusão e Expedição de Documentos** — encerramento do vínculo e validação final da elegibilidade do aluno.
- Comprovação de estágio obrigatório e TCC: entrega do termo de realização de estágio, relatório final assinado e termo de depósito da monografia, dissertação ou tese no repositório institucional.
- Certidão de nada consta: quitação de débitos com a biblioteca institucional, laboratórios e setor financeiro.
- Requerimento de colação de grau ou diplomação: verificação completa da pasta documental física ou digital para viabilizar o diploma digital e o histórico final.

Mapeamento para os campos: o texto antes dos dois-pontos vira `name`; o texto depois vira `description`; `required_documents` recebe os documentos citados na descrição (ex.: RG, CPF, certidão…) quando houver; `purpose` e `operational_note` ficam vazios na carga inicial e são preenchidos pela secretaria (não inventar conteúdo).

### 10.4 Front-end
Tela administrativa nova (ex.: `frontend/app/finance/procedures/page.tsx`), sem alterar as telas existentes: lista agrupada por categoria, busca, formulário de criar/editar, modal de detalhes e confirmação antes de eliminar. Aluno vê somente a consulta. Landing pages não são alteradas.

### 10.5 Testes do CRUD
Unitário (validação e regras), integração com Postgres real (criação, unicidade parcial, exclusão lógica sem erro, carga inicial idempotente: rodar duas vezes não duplica), segurança (aluno não escreve; escrita só com perfil autorizado), regressão (cobranças e faturas existentes inalteradas).


## 11. Tabela de valores das cobranças (com controle de acesso e confirmação)

Tabela única com todas as cobranças cobráveis do aluno, para preenchimento dos valores. Faz parte da especificação (nenhum código foi alterado). A lista de linhas é a do cadastro de procedimentos da seção 10, mais as cobranças recorrentes que o sistema já conhece (`EPaymentCategory`).

### 11.1 Perfis e permissões

| Perfil | Ver a tabela | Propor/alterar valor | Confirmar alteração |
|---|---|---|---|
| Administrador (`ROLE_ADMIN`, `ROLE_ROOT_MASTER`) | Todas as linhas | Todas as linhas; vale na hora e fica no histórico | Sim, é o único perfil que confirma |
| Chefe de secretaria | Só as linhas sob sua responsabilidade | Só as suas; gera **proposta pendente** | Não |
| Secretaria (`ROLE_SECRETARIA`) | Só as linhas do(s) seu(s) curso(s) | Não (somente leitura) | Não |
| Demais perfis (financeiro, aluno, coordenador, etc.) | **Sem acesso** | Não | Não |

Regra central: **nenhum valor proposto por chefe de secretaria entra em vigor sem a confirmação de um administrador.**

### 11.2 Colunas da tabela

| Coluna | Conteúdo | Quem edita | Depende de confirmação |
|---|---|---|---|
| Código | Identificador da cobrança | ninguém (gerado) | n/a |
| Categoria | Uma das 5 categorias da seção 10 (ou "Cobranças recorrentes") | administrador | não |
| Cobrança / procedimento | Nome (ex.: "Rematrícula periódica") | administrador | não |
| Escopo | Graduação, Pós ou Todos | administrador | não |
| Curso | Curso ao qual o valor se aplica (ou "Todos") | administrador | não |
| Secretaria responsável | Secretaria/chefia dona da linha | administrador | não |
| **Valor vigente (R$)** | Valor em vigor, em reais com 2 casas | administrador (direto) | não |
| **Valor proposto (R$)** | Valor sugerido, ainda não vigente | administrador e chefe de secretaria | **sim**: só vira vigente quando o administrador confirma |
| Situação | Vigente, Proposta pendente, Rejeitada | sistema | n/a |
| Proposto por / em | Quem sugeriu e quando | sistema | n/a |
| Confirmado por / em | Administrador que decidiu e quando | sistema | n/a |
| Motivo | Justificativa da proposta ou da rejeição | chefe (proposta), administrador (rejeição) | n/a |

Visual por perfil: coluna editável com campo habilitado; coluna só leitura em cinza; "Valor proposto" com selo amarelo "aguardando confirmação"; botões **Confirmar** e **Rejeitar** só para administrador.

### 11.3 Linhas (valores a preencher)

Os valores ficam em branco para preenchimento. "Todos" = graduação e pós.

| # | Categoria | Cobrança | Escopo | Valor vigente (R$) |
|---|---|---|---|---|
| 1 | Ingresso e Matrícula | Entrega de documentação civil e escolar | Todos | ______ |
| 2 | Ingresso e Matrícula | Comprovação de escolaridade anterior | Todos | ______ |
| 3 | Ingresso e Matrícula | Assinatura do contrato e aceite regimental | Todos | ______ |
| 4 | Renovação e Ajustes Curriculares | Rematrícula periódica | Todos | ______ |
| 5 | Renovação e Ajustes Curriculares | Ajuste de matrícula | Todos | ______ |
| 6 | Renovação e Ajustes Curriculares | Aproveitamento de estudos e equivalências | Todos | ______ |
| 7 | Renovação e Ajustes Curriculares | Trancamento ou cancelamento de curso | Todos | ______ |
| 8 | Acompanhamento da Vida Escolar | Atualização cadastral contínua | Todos | ______ |
| 9 | Acompanhamento da Vida Escolar | Justificativas e regimes especiais | Todos | ______ |
| 10 | Acompanhamento da Vida Escolar | Protocolos de segunda chamada | Todos | ______ |
| 11 | Acompanhamento da Vida Escolar | Atividades complementares | Graduação | ______ |
| 12 | Exigências Específicas da Pós-Graduação | Registro de orientação | Pós | ______ |
| 13 | Exigências Específicas da Pós-Graduação | Exame de qualificação | Pós | ______ |
| 14 | Exigências Específicas da Pós-Graduação | Composição de bancas | Pós | ______ |
| 15 | Exigências Específicas da Pós-Graduação | Comprovação de proficiência em língua estrangeira | Pós | ______ |
| 16 | Conclusão e Expedição de Documentos | Comprovação de estágio obrigatório e TCC | Todos | ______ |
| 17 | Conclusão e Expedição de Documentos | Certidão de nada consta | Todos | ______ |
| 18 | Conclusão e Expedição de Documentos | Requerimento de colação de grau ou diplomação | Todos | ______ |
| 19 | Cobranças recorrentes | Mensalidade (`TUITION`) | Todos | ______ |
| 20 | Cobranças recorrentes | Taxa de matrícula (`ENROLLMENT_FEE`) | Todos | ______ |
| 21 | Cobranças recorrentes | Solicitação de documento (`DOCUMENT_REQUEST`) | Todos | ______ |
| 22 | Cobranças recorrentes | Taxa de laboratório (`LAB_FEE`) | Todos | ______ |
| 23 | Cobranças recorrentes | Processo interno (`INTERNAL_PROCESS`) | Todos | ______ |
| 24 | Cobranças recorrentes | Outras (`OTHER`) | Todos | ______ |

Observação: procedimentos que podem ser gratuitos ficam com `0,00`; o sistema deve aceitar zero e recusar valores negativos.

### 11.4 Modelo de dados (migração aditiva, após as da seção 10)

| Tabela | Campos principais |
|---|---|
| `charge_prices` | `id`, `procedure_id` (FK; nulo para recorrentes) ou `charge_category`, `course_id` (nulo = todos), `academic_scope`, `secretariat_id`, `current_value NUMERIC(12,2) CHECK (>= 0)`, `version`, auditoria |
| `charge_price_changes` | `id`, `charge_price_id` (FK), `proposed_value NUMERIC(12,2) CHECK (>= 0)`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `proposed_by`, `proposed_at`, `reason`, `decided_by`, `decided_at`, `decision_reason` |
| `staff_course_scope` | `staff_id`, `course_id`, `is_secretariat_head` (diz quem é chefe e de quais cursos) |

Regras: no máximo uma proposta `PENDING` por cobrança (índice único parcial); aprovar atualiza `current_value` e marca a proposta `APPROVED` na **mesma transação**; o histórico nunca é apagado.

### 11.5 API e regras de acesso (no servidor, nunca só na tela)

| Operação | Rota | Quem |
|---|---|---|
| Listar | `GET /api/finance/prices` | administrador (tudo); chefe e secretaria (só o seu escopo); demais: 403 |
| Alterar valor (admin) | `PUT /api/finance/prices/{id}` | administrador |
| Propor valor | `POST /api/finance/prices/{id}/proposals` | chefe de secretaria do escopo |
| Confirmar / rejeitar | `POST /api/finance/prices/proposals/{id}/approve` e `/reject` | somente administrador |
| Histórico | `GET /api/finance/prices/{id}/history` | quem pode ver a linha |

O filtro de escopo é aplicado na consulta (não se confia no que a tela envia). O chefe nunca aprova a própria proposta. Alterações e decisões geram registro de auditoria.

### 11.6 Suposições e pontos a decidir
1. **Não existe hoje o perfil "chefe de secretaria"** (os perfis atuais vão de `ROLE_ROOT_MASTER` a `ROLE_CANDIDATO`). Sugestão: marcar o chefe em `staff_course_scope`, em vez de criar um perfil novo.
2. **Não existe vínculo entre secretaria e curso** no sistema. A tabela `staff_course_scope` cria esse vínculo e precisa ser preenchida (quem atende cada curso).
3. **"Sob sua responsabilidade"** foi interpretado como as cobranças dos cursos do chefe. Confirmar se é por curso ou por categoria.
4. **O perfil Financeiro fica sem acesso**, pela regra de que só administradores e secretarias veem a tabela. Hoje o financeiro lança cobranças; confirmar se isso deve mudar.
5. **O valor é por cobrança e curso** (ou global, quando o curso é "Todos"). Confirmar se o valor pode variar por curso.
6. **Sem data de vigência:** a confirmação vale a partir do momento. Se for preciso vigência futura, acrescentar um campo de data.
7. **Cobranças já emitidas não mudam** quando o valor da tabela muda; só valem para as novas.
