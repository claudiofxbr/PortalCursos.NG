# Relatório de testes — PortalCursos.NG

**Data da execução:** 20/10/2026
**Versão testada:** `main` em `a23f2e9` (PR #126), em produção em https://xavierbr-vps.tech/portalcursos.ng
**Executado por:** Claude Code, a pedido do responsável pelo projeto

---

## 1. Resumo geral

| Frente | Casos | Passaram | Falharam | Observação |
|---|---|---|---|---|
| Testes automatizados do backend (local) | 187 | 186 | 1 (ambiental) | A falha é o teste de migração, que exige Docker (desligado nesta máquina) |
| Testes automatizados do backend (CI do GitHub, mesma versão) | 200 | 200 | 0 | Inclui o teste de migração em Postgres real (14 casos) |
| Testes automatizados do frontend | 39 | 39 | 0 | 5 arquivos; `tsc` e `next build` sem erro |
| Caixa-preta em produção, sem login (seção 4) | 103 | 99 (102 após o reteste do E-01) | 4 (1 após o reteste) | Restam abertos E-02 (cabeçalho Server) |
| Verificação visual no navegador (seção 5) | 4 | 3 | 1 | Achado visual na tela de acesso |

**Conclusão:** o aplicativo está disponível e estável, a autorização funciona (nenhuma rota protegida abriu sem login) e não há vazamento de segredos. Foram encontrados **3 defeitos** (E-01 a E-03; o **E-01 foi corrigido e confirmado em produção em 20/10/2026**, ver seção 10), de gravidade baixa ou média e sem impacto sobre dados, e 5 observações (E-04 a E-08). Os 4 casos de caixa-preta que falharam correspondem a E-01 (3 casos) e E-02 (1 caso). O mais relevante é que erros de entrada do cliente viram **HTTP 500** em vez de 400/405 (E-01). **Os fluxos que exigem login não foram exercitados em produção** (limitação da seção 8), apenas pelos testes automatizados.

---

## 2. Escopo, ambiente e método

- **Alvo:** produção (`https://xavierbr-vps.tech`), versão `a23f2e9`; código local no mesmo commit.
- **Dados:** somente fictícios. Nenhuma credencial real foi usada, nenhuma conta foi criada e nenhuma fatura foi gerada.
- **Métodos:**
  1. Suítes automatizadas completas (`./mvnw test`, `vitest`, `tsc`, `eslint`, `next build`).
  2. 103 casos de caixa-preta com `curl` contra produção (script reproduzível, sem login).
  3. Verificação visual da tela de acesso no navegador (desktop e celular).
  4. Carga leve de sanidade (até 10 requisições em paralelo).
- **Convenção:** ✅ PASSOU = resultado igual ao esperado; ❌ FALHOU = diferente do esperado (detalhado na seção 7).

---

## 3. Testes automatizados (código)

### 3.1 Backend (Spring Boot, Java 17 local / 21 no CI)

| Classe | Funcionalidade coberta | Casos | Resultado |
|---|---|---|---|
| AuthControllerIntegrationTest | Login, cadastro, sessão, logout; casos de erro | 12 | ✅ |
| UserControllerIntegrationTest | Gestão de usuários e papéis | 4 | ✅ |
| AcademicControllerIntegrationTest | Consultas acadêmicas | 4 | ✅ |
| CourseControllerIntegrationTest | Cursos (CRUD e permissões) | 3 | ✅ |
| GradStudentControllerIntegrationTest | Matrícula e alunos de graduação | 4 | ✅ |
| PostgradStudentControllerIntegrationTest | Alunos de pós-graduação | 5 | ✅ |
| StaffMemberControllerIntegrationTest | Colaboradores | 4 | ✅ |
| RepairControllerIntegrationTest | Chamados de manutenção | 3 | ✅ |
| DocumentControllerIntegrationTest | Documentos/uploads com controle de acesso | 3 | ✅ |
| PrivacyControllerIntegrationTest | LGPD (meus dados, exclusão, anonimização) | 9 | ✅ |
| HealthControllerIntegrationTest | Saúde e migrações | 2 | ✅ |
| FinancialControllerIntegrationTest | Faturas, Pix, boleto, permissões | 9 | ✅ |
| CardCheckoutControllerIntegrationTest | Checkout de cartão e webhook público | 6 | ✅ |
| ChargePriceControllerIntegrationTest | Tabela de valores: matriz de acesso, proposta, confirmação | 16 | ✅ |
| PixWebhookControllerTest, CardWebhookControllerTest | Webhooks: token, reconsulta, baixa, 503, concorrência | 5 + 6 | ✅ |
| CardPaymentReleaseServiceTest | Regra de baixa (referência, valor, idempotência, cancelada) | 9 | ✅ |
| CardCheckoutServiceTest, CardCheckoutRateLimiterTest | Criação do checkout, reaproveitamento, limite 5 por 10 min | 7 + 4 | ✅ |
| PagBankCheckoutGatewayServiceTest, PagBankCheckoutFactsTest, PagBankPixGatewayServiceTest | Cliente do PagBank (HTTP simulado): erros, links inseguros, soma de cobranças | 7 + 9 + 9 | ✅ |
| PagBankEnvironmentPolicyTest | Trava de sandbox (admin/testador) | 4 | ✅ |
| PaymentServiceTest, PaymentCodeGenerationServiceTest, PaymentAuthorizationServiceTest | Cobranças, geração de Pix/boleto, propriedade da fatura | 8 + 7 + 10 | ✅ |
| ChargePriceValueTest | Validação de valores (0,00 a 9.999.999,99, 2 casas) | 2 | ✅ |
| DataAnonymizationServiceTest, StudentServiceTest, PostgradStudentServiceTest, UserServiceTest, LoginAttemptServiceConcurrencyTest, GlobalExceptionHandlerTest | Regras de serviço e concorrência de tentativas de login | 5 + 3 + 1 + 4 + 1 + 1 | ✅ |
| **PostgresMigrationIntegrationTest** | Migrações V1–V25 em Postgres real, soft-delete, índices parciais, tabela de valores | 1 local / 14 no CI | ⚠️ local (sem Docker) / ✅ no CI |

**Total local:** 187 casos, 186 passaram, 1 erro de ambiente (`Could not find a valid Docker environment`). **No CI, a mesma versão passou 200/200.**

### 3.2 Frontend (Next.js 16, Vitest + Testing Library)

| Arquivo | Funcionalidade | Casos | Resultado |
|---|---|---|---|
| auth/signin.test.tsx e academic/grad-enroll.test.tsx | Tela de login; matrícula (listar, remover com confirmação, erros) | 11 (somados) | ✅ |
| finance/card-checkout.test.tsx | Botão de cartão, link seguro, retorno do PagBank | 9 | ✅ |
| finance/price-table.test.tsx | Tabela de valores por perfil | 10 | ✅ |
| finance/price-admin.test.tsx | Administração de vínculos e valores por curso | 9 | ✅ |

`tsc --noEmit`: sem erros. `next build`: compilou e gerou as rotas (inclui `/finance/prices` e `/finance/prices/admin`). `eslint app`: **82 erros e 57 avisos**, em sua maioria `no-explicit-any` já existentes em páginas antigas (E-05).

---

## 4. Caixa-preta em produção (sem login)

Cada caso foi executado contra `https://xavierbr-vps.tech`. Passos: enviar a requisição indicada (com `curl`), comparar o código HTTP ou o conteúdo com o esperado.

| ID | Grupo | Caso e passos | Esperado | Obtido | Status |
|---|---|---|---|---|---|
| BB-01 | Disponibilidade | GET /portalcursos.ng/ | 200 | 200 | ✅ PASSOU |
| BB-02 | Disponibilidade | GET /portalcursos.ng/auth/signin | 200 | 200 | ✅ PASSOU |
| BB-03 | Disponibilidade | GET /portalcursos.ng/auth/signup | 200 | 200 | ✅ PASSOU |
| BB-04 | Disponibilidade | GET /portalcursos.ng/courses | 200 | 200 | ✅ PASSOU |
| BB-05 | Disponibilidade | GET /portalcursos.ng/finance | 200 | 200 | ✅ PASSOU |
| BB-06 | Disponibilidade | GET /portalcursos.ng/finance/prices | 200 | 200 | ✅ PASSOU |
| BB-07 | Disponibilidade | GET /portalcursos.ng/finance/prices/admin | 200 | 200 | ✅ PASSOU |
| BB-08 | Disponibilidade | GET /portalcursos.ng/users | 200 | 200 | ✅ PASSOU |
| BB-09 | Disponibilidade | GET /portalcursos.ng/repairs | 200 | 200 | ✅ PASSOU |
| BB-10 | Disponibilidade | GET /portalcursos.ng/roadmap | 200 | 200 | ✅ PASSOU |
| BB-11 | Disponibilidade | GET /portalcursos.ng/privacidade | 200 | 200 | ✅ PASSOU |
| BB-12 | Disponibilidade | GET /portalcursos.ng/privacidade-solicitacoes | 200 | 200 | ✅ PASSOU |
| BB-13 | Disponibilidade | GET /portalcursos.ng/minha-conta | 200 | 200 | ✅ PASSOU |
| BB-14 | Disponibilidade | GET /portalcursos.ng/academic/enroll | 200 | 200 | ✅ PASSOU |
| BB-15 | Disponibilidade | GET /portalcursos.ng/academic/postgrad | 200 | 200 | ✅ PASSOU |
| BB-16 | Disponibilidade | GET /portalcursos.ng/health | 200 | 200 | ✅ PASSOU |
| BB-17 | Disponibilidade | GET /portalcursos.ng/version | 200 | 200 | ✅ PASSOU |
| BB-18 | Disponibilidade | Pagina inexistente devolve 404 | 404 | 404 | ✅ PASSOU |
| BB-19 | Disponibilidade | API health status UP | UP | UP | ✅ PASSOU |
| BB-20 | Disponibilidade | Banco CONNECTED | CONNECTED | CONNECTED | ✅ PASSOU |
| BB-21 | Disponibilidade | Migracoes OK | OK | OK | ✅ PASSOU |
| BB-22 | Disponibilidade | Ultima migracao aplicada = 25 | 25 | 25 | ✅ PASSOU |
| BB-23 | Autenticacao | Login com credenciais ficticias | 401 | 401 | ✅ PASSOU |
| BB-24 | Autenticacao | Login com corpo vazio | 400 ou 401 | 400 | ✅ PASSOU |
| BB-25 | Autenticacao | Login com JSON malformado | 400 ou 401 | 400 | ✅ PASSOU |
| BB-26 | Autenticacao | Login com texto de injecao SQL no usuario (nao pode dar 500) | 400 ou 401 | 401 | ✅ PASSOU |
| BB-27 | Autenticacao | Login com usuario de 20 mil caracteres (nao pode dar 500) | 400 ou 401 ou 413 | 401 | ✅ PASSOU |
| BB-28 | Autenticacao | Sessao atual sem cookie | 401 | 401 | ✅ PASSOU |
| BB-29 | Autenticacao | Renovar token sem cookie | 400 ou 401 ou 403 | 400 | ✅ PASSOU |
| BB-30 | Autenticacao | Cadastro sem corpo | 400 ou 401 ou 403 | 400 | ✅ PASSOU |
| BB-31 | Autenticacao | Cabecalho Authorization forjado e ignorado | 401 | 401 | ✅ PASSOU |
| BB-32 | Autenticacao | Cookie de sessao forjado e recusado | 401 | 401 | ✅ PASSOU |
| BB-33 | Autorizacao | GET /api/v1/users sem login | 401 | 401 | ✅ PASSOU |
| BB-34 | Autorizacao | GET /api/v1/staff sem login | 401 | 401 | ✅ PASSOU |
| BB-35 | Autorizacao | GET /api/v1/courses sem login | 401 | 401 | ✅ PASSOU |
| BB-36 | Autorizacao | GET /api/v1/grad-students sem login | 401 | 401 | ✅ PASSOU |
| BB-37 | Autorizacao | GET /api/v1/postgrad-students sem login | 401 | 401 | ✅ PASSOU |
| BB-38 | Autorizacao | GET /api/academic/students sem login | 401 | 401 | ✅ PASSOU |
| BB-39 | Autorizacao | GET /api/academic/users sem login | 401 | 401 | ✅ PASSOU |
| BB-40 | Autorizacao | GET /api/repairs sem login | 401 | 401 | ✅ PASSOU |
| BB-41 | Autorizacao | GET /api/finance/invoices sem login | 401 | 401 | ✅ PASSOU |
| BB-42 | Autorizacao | GET /api/finance/history sem login | 401 | 401 | ✅ PASSOU |
| BB-43 | Autorizacao | GET /api/finance/invoices/GRADUATION sem login | 401 | 401 | ✅ PASSOU |
| BB-44 | Autorizacao | GET /api/finance/student/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-45 | Autorizacao | GET /api/finance/prices sem login | 401 | 401 | ✅ PASSOU |
| BB-46 | Autorizacao | GET /api/finance/prices/1/history sem login | 401 | 401 | ✅ PASSOU |
| BB-47 | Autorizacao | GET /api/finance/price-scopes sem login | 401 | 401 | ✅ PASSOU |
| BB-48 | Autorizacao | GET /api/privacy/my-data sem login | 401 | 401 | ✅ PASSOU |
| BB-49 | Autorizacao | GET /api/privacy/data-deletion-requests sem login | 401 | 401 | ✅ PASSOU |
| BB-50 | Autorizacao | GET /api/uploads/qualquer.pdf sem login | 401 | 401 | ✅ PASSOU |
| BB-51 | Autorizacao | POST /api/finance/charge sem login | 401 | 401 | ✅ PASSOU |
| BB-52 | Autorizacao | POST /api/finance/generate-pix/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-53 | Autorizacao | POST /api/finance/generate-boleto/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-54 | Autorizacao | POST /api/finance/generate-card-checkout/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-55 | Autorizacao | POST /api/finance/prices/course-rows sem login | 401 | 401 | ✅ PASSOU |
| BB-56 | Autorizacao | POST /api/finance/prices/1/proposals sem login | 401 | 401 | ✅ PASSOU |
| BB-57 | Autorizacao | POST /api/finance/prices/proposals/1/approve sem login | 401 | 401 | ✅ PASSOU |
| BB-58 | Autorizacao | POST /api/finance/price-scopes sem login | 401 | 401 | ✅ PASSOU |
| BB-59 | Autorizacao | PUT /api/finance/prices/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-60 | Autorizacao | PUT /api/finance/invoices/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-61 | Autorizacao | DELETE /api/finance/invoices/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-62 | Autorizacao | DELETE /api/finance/price-scopes/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-63 | Autorizacao | POST /api/v1/users sem login | 401 | 401 | ✅ PASSOU |
| BB-64 | Autorizacao | DELETE /api/v1/users/1 sem login | 401 | 401 | ✅ PASSOU |
| BB-65 | Autorizacao | DELETE /api/v1/users/security/clear-all-blocks sem login | 401 | 401 | ✅ PASSOU |
| BB-66 | Autorizacao | DELETE /api/v1/courses/00000000-0000-0000-0000-000000000000 sem login | 401 | 401 | ✅ PASSOU |
| BB-67 | Autorizacao | POST /api/v1/courses sem login | 401 | 401 | ✅ PASSOU |
| BB-68 | Autorizacao | POST /api/privacy/data-deletion-request sem login | 401 | 401 | ✅ PASSOU |
| BB-69 | Pagamentos | Webhook Pix com token invalido | 404 | 404 | ✅ PASSOU |
| BB-70 | Pagamentos | Webhook cartao com token invalido | 404 | 404 | ✅ PASSOU |
| BB-71 | Pagamentos | Webhook Pix com id nao numerico | 400 ou 404 | 500 → **400** após a correção (reteste) | ✅ PASSOU no reteste |
| BB-72 | Pagamentos | Webhook cartao com id enorme | 400 ou 404 | 500 → **400** após a correção (reteste) | ✅ PASSOU no reteste |
| BB-73 | Pagamentos | Webhook Pix nao aceita GET | 405 ou 404 ou 401 | 500 → **405** com `Allow: POST` após a correção (reteste) | ✅ PASSOU no reteste |
| BB-74 | Pagamentos | Webhook cartao com corpo forjado nao baixa nada (404 sem token valido) | 404 | 404 | ✅ PASSOU |
| BB-75 | Seguranca | HTTP redireciona para HTTPS | 301 ou 302 ou 307 ou 308 | 301 | ✅ PASSOU |
| BB-76 | Seguranca | Cabecalho strict-transport-security na API | present | present | ✅ PASSOU |
| BB-77 | Seguranca | Cabecalho x-content-type-options na API | present | present | ✅ PASSOU |
| BB-78 | Seguranca | Cabecalho x-frame-options (ou frame-ancestors) na API | present | present | ✅ PASSOU |
| BB-79 | Seguranca | Cabecalho strict-transport-security no frontend | present | present | ✅ PASSOU |
| BB-80 | Seguranca | Cabecalho x-content-type-options no frontend | present | present | ✅ PASSOU |
| BB-81 | Seguranca | Cabecalho x-frame-options (ou frame-ancestors) no frontend | present | present | ✅ PASSOU |
| BB-82 | Seguranca | Frontend nao expoe X-Powered-By | absent | absent | ✅ PASSOU |
| BB-83 | Seguranca | API nao expoe cabecalho Server com versao | absent | present | ❌ FALHOU |
| BB-84 | Seguranca | CORS nao libera origem desconhecida | absent | absent | ✅ PASSOU |
| BB-85 | Seguranca | CORS nao libera curinga com credenciais | absent | absent | ✅ PASSOU |
| BB-86 | Exposicao | GET /actuator nao e publico | 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-87 | Exposicao | GET /actuator/env nao e publico | 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-88 | Exposicao | GET /actuator/heapdump nao e publico | 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-89 | Exposicao | GET /v3/api-docs nao e publico | 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-90 | Exposicao | GET /swagger-ui/index.html nao e publico | 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-91 | Exposicao | GET /api/actuator/env nao e publico | 401 ou 403 ou 404 | 401 | ✅ PASSOU |
| BB-92 | Exposicao | GET /h2-console nao e publico | 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-93 | Exposicao | GET /portalcursos.ng/.env nao e servido | 400 ou 401 ou 403 ou 404 ou 308 | 404 | ✅ PASSOU |
| BB-94 | Exposicao | GET /portalcursos.ng/.git/config nao e servido | 400 ou 401 ou 403 ou 404 ou 308 | 404 | ✅ PASSOU |
| BB-95 | Exposicao | GET /portalcursos.ng/package.json nao e servido | 400 ou 401 ou 403 ou 404 ou 308 | 404 | ✅ PASSOU |
| BB-96 | Exposicao | GET /portalcursos.ng/_next/../.env nao e servido | 400 ou 401 ou 403 ou 404 ou 308 | 404 | ✅ PASSOU |
| BB-97 | Exposicao | Traversal em /api/uploads/ bloqueado | 400 ou 401 ou 403 ou 404 | 404 | ✅ PASSOU |
| BB-98 | Exposicao | Erro de JSON malformado nao vaza stack trace | clean | clean | ✅ PASSOU |
| BB-99 | Exposicao | 404 de API nao vaza stack trace | clean | clean | ✅ PASSOU |
| BB-100 | Exposicao | Health nao expõe segredos (token, senha, jdbc) | clean | clean | ✅ PASSOU |
| BB-101 | Exposicao | Metodo TRACE desabilitado | 400 ou 401 ou 403 ou 404 ou 405 ou 501 | 405 | ✅ PASSOU |
| BB-102 | Carga leve | 60 requisições a /api/health, 10 em paralelo | 60/60 respostas 200 | 60/60 (p50 804 ms, p95 1090 ms, máx 1122 ms) | ✅ PASSOU |
| BB-103 | Carga leve | 40 requisições à página de login, 8 em paralelo | 40/40 respostas 200 | 40/40 (p50 565 ms, p95 602 ms, máx 728 ms) | ✅ PASSOU |

**Nota sobre a home:** `GET /portalcursos.ng` (sem barra final) responde 308 (redirecionamento normal); com a barra e com `-L` responde 200. Não é defeito.

---

## 5. Verificação visual no navegador (tela de acesso)

| ID | Caso e passos | Esperado | Obtido | Status |
|---|---|---|---|---|
| UI-01 | Abrir `/portalcursos.ng/auth/signin` | Tela de acesso carrega, sem erro de recurso | Carregou; CSS e scripts 200. Aparece primeiro a camada **"Acesso Restrito — Código de Acesso"** (senha global), antes do login | ✅ PASSOU |
| UI-02 | Enviar o código de acesso vazio | Validação ou mensagem clara | O envio chega ao servidor (`POST /portal-auth` → 400) e a tela mostra "Senha incorreta. Tente novamente." Sem validação no navegador | ✅ PASSOU (com observação, E-03) |
| UI-03 | Conferir o console | Sem erros de aplicação | 4 mensagens "401" (consulta de sessão `/api/auth/me` sem login, a cada carga) | ✅ PASSOU (ruído, E-04) |
| UI-04 | Layout em desktop e celular (375x812) | Tela organizada, ícone proporcional | **Ícone de cadeado ocupa a largura inteira**, textos sem estilo; funciona, mas visualmente quebrado (igual nos dois tamanhos) | ❌ FALHOU (E-03) |

Não foi inserido nenhum código de acesso nem credencial na tela.

---

## 6. Fluxos críticos

| Fluxo | Como foi verificado | Situação |
|---|---|---|
| Login e sessão | Testes de integração (12) + negativos em produção (credencial falsa, JSON malformado, injeção SQL, corpo de 20 mil caracteres, cookie/token forjado) | ✅ Negativos passam. Login **válido** não foi testado em produção |
| Autorização por perfil | 36 rotas protegidas sem login (todas 401) + matriz de acesso em testes (admin, chefe, secretaria, financeiro, aluno, sem escopo) | ✅ |
| Pix (geração e baixa) | Testes unitários e de integração com PagBank simulado; webhook com token inválido (404) em produção | ✅ em teste. **Pagamento real no sandbox ainda não foi feito** |
| Cartão (checkout e baixa) | Testes unitários/integração (baixa só com PAID + referência + valor iguais; 503 em falha do PSP) | ✅ em teste. Botão **oculto** em produção e débito aguardando o PagBank |
| Tabela de valores | 16 testes de integração (matriz de acesso, proposta, confirmação, versão, valor inválido) + V25 aplicada em produção (migração versão 25) | ✅ em teste. Tela não vista logada |
| Migrações de banco | Postgres real no CI (14 casos) + health de produção (`migrations: OK`, última 25) | ✅ |
| Privacidade (LGPD) | 9 testes de integração + anonimização | ✅ em teste |

---

## 7. Erros e inconsistências encontrados

| ID | Gravidade | Onde | O que ocorre | Como reproduzir | O que corrigir |
|---|---|---|---|---|---|
| **E-01** ✅ **CORRIGIDO** | **Média** | `GlobalExceptionHandler` (backend) | Erros do cliente viram **HTTP 500** ("Exceção não tratada"): (a) id não numérico ou fora do intervalo em `{paymentId}` (`MethodArgumentTypeMismatchException`); (b) método HTTP errado em rota existente (`HttpRequestMethodNotSupportedException`). Cada caso gera um `ERROR` no log do servidor (ruído para alertas). Não vaza stack trace nem segredo (a resposta traz só um protocolo de rastreio). Afeta todas as rotas, não só os webhooks | `POST /api/finance/pix/webhook/abc/x` → 500; `GET /api/finance/pix/webhook/1/x` → 500; `POST /api/finance/card/webhook/99999999999999999999999/x` → 500 | Tratar `MethodArgumentTypeMismatchException` e erros de conversão como 400, `HttpRequestMethodNotSupportedException` como 405 (e `HttpMediaTypeNotSupportedException` como 415), no `GlobalExceptionHandler`, com teste de regressão |
| E-02 | Baixa | nginx do servidor | O cabeçalho `Server: nginx/1.24.0 (Ubuntu)` revela produto e versão (BB-83) | `curl -I https://xavierbr-vps.tech/api/health` | `server_tokens off;` na configuração do nginx (infra, exige autorização) |
| E-03 | Baixa (visual) | Tela "Acesso Restrito" (camada de código de acesso) | Ícone de cadeado sem tamanho definido ocupa toda a largura; textos sem estilo; o envio vazio vai ao servidor e responde "Senha incorreta" | Abrir `/portalcursos.ng/auth/signin` | Definir tamanho do ícone e estilo da camada; validar campo vazio no navegador |
| E-04 | Informativa | Frontend | Cada carga de página faz `GET /api/auth/me` e registra 401 no console antes do login | Abrir qualquer página sem sessão | Usar um modo "opcional" sem 401 (o CVFacil.NG já faz assim) |
| E-05 | Informativa | Frontend (qualidade) | `eslint` com 82 erros e 57 avisos (maioria `no-explicit-any` já existente) | `npx eslint app` | Reduzir gradualmente; não bloqueia o build |
| E-06 | Informativa | Ambiente local | O teste de migração em Postgres real não roda sem Docker | `./mvnw test` com Docker desligado | Ligar o Docker local; no CI já roda e passa |
| E-07 | Observação | Desempenho | `/api/health` com 10 requisições em paralelo: p50 804 ms, p95 1090 ms. A latência do banco no health é ~250 ms (Neon remoto). Sem falhas | Seção 4, BB-102 | Não é defeito; acompanhar se a carga real crescer. Considerar cache curto no health |
| E-08 | Observação | Segurança / operação | GitHub aponta 6 vulnerabilidades de dependências (2 críticas, 1 alta) e há 15 PRs do Dependabot abertos (não analisado nesta rodada); `.env` da VPS com permissão 644; sobras de contêineres Traefik "Created" na VPS | — | Triar vulnerabilidades; `chmod 600` no `.env`; limpar contêineres órfãos (todos com autorização) |

---

## 8. Suposições e limitações

1. **Sem login:** não havia credencial de teste e não criei conta nem usei senha real. Portanto **não foram exercitados em produção:** login válido, listagens com dados reais, matrícula, upload de documentos, tabela de valores e administração logadas, geração de Pix/boleto e baixa por webhook com PagBank real.
2. **Força bruta e bloqueio de IP não foram testados** (para não bloquear o IP do responsável). Está coberto só pelos testes automatizados.
3. **Pix e cartão:** em sandbox do PagBank; o roteiro de teste de Pix (faturas de R$ 50 a R$ 350) está pendente de execução pelo responsável. O simulador do PagBank pode não cobrir Pix criado pela API de pedidos.
4. **Carga/estresse:** apenas sanidade leve (≤10 em paralelo). Não houve teste de estresse nem de volume.
5. **Camada de código de acesso:** assumi que a tela "Acesso Restrito" é intencional (senha global do portal) e testei apenas o comportamento sem credencial.
6. **Ambiente local:** Java 17 (o projeto usa Java 21) e sem Docker; os resultados locais foram confirmados pelo CI.
7. **Segurança:** os testes foram não destrutivos e de superfície (cabeçalhos, exposição, autorização). Não substituem um teste de intrusão.

---

## 9. Observações e recomendações finais

1. ~~**Corrigir E-01 primeiro**~~ **Feito** (PR #127, `861e94c`): 400/405/415 no lugar de 500, com 10 testes de regressão. Restam E-02 e E-03.
2. **Executar o roteiro de Pix no sandbox** e um ciclo completo logado (administrador, chefe, secretaria) para fechar as lacunas da seção 8.
3. **Passar a variável de build do botão de cartão** só depois do Pix validado, e habilitar o débito quando o PagBank aprovar.
4. **Ajustes de infraestrutura** (autorização necessária): `server_tokens off`, `chmod 600` no `.env`, e atualizar a nota do `CLAUDE.md` sobre o edge (hoje é o nginx).
5. **Qualidade contínua:** reduzir os avisos do `eslint`, triar as vulnerabilidades do Dependabot e manter o teste de Postgres real no CI (já está).
6. **Pontos fortes observados:** autorização consistente (36/36 rotas protegidas), erros sem vazamento de stack, CORS restritivo, HSTS e demais cabeçalhos presentes, nenhuma exposição de arquivos sensíveis ou endpoints de administração, e cobertura automatizada ampla das regras de pagamento e de acesso.

---

## 10. Reteste após a correção do E-01

**Correção:** PR #127, merge `861e94c`, em produção em 20/10/2026 (deploy com sucesso, health `UP`, migrações OK na versão 25). Alteração apenas no `GlobalExceptionHandler`: `TypeMismatchException` → 400, `HttpRequestMethodNotSupportedException` → 405 (com `Allow`), `HttpMediaTypeNotSupportedException` → 415.

**Testes de regressão no código:** 4 unitários (`GlobalExceptionHandlerTest`) + 6 de integração (`ClientErrorHandlingIntegrationTest`). Sem a correção, 5 dos 6 de integração falhavam com 500; com ela, todos passam. No CI: 210 testes, 0 falhas.

| ID | Caso e passos (produção) | Antes | Depois | Status |
|---|---|---|---|---|
| BB-71 | `POST /api/finance/pix/webhook/abc/x` | 500 | 400 ("O parâmetro 'paymentId' tem um valor inválido.", sem repetir o valor) | ✅ PASSOU |
| BB-72 | `POST /api/finance/card/webhook/99999999999999999999999/x` | 500 | 400 | ✅ PASSOU |
| BB-73 | `GET /api/finance/pix/webhook/1/x` | 500 | 405 com `Allow: POST` | ✅ PASSOU |
| RT-04 | `POST` com `Content-Type: text/plain` no webhook do Pix | 500 | 415 | ✅ PASSOU |
| RT-05 | Webhook Pix e cartão com id numérico e token inválido | 404 | 404 (sem regressão) | ✅ PASSOU |
| RT-06 | Rota protegida sem login; login com credencial falsa; JSON malformado | 401 / 401 / 400 | 401 / 401 / 400 (sem regressão) | ✅ PASSOU |
| RT-07 | Logs do backend (10 min após o deploy) | `ERROR` a cada caso | 0 `ERROR` (exceto os 401 esperados); 5 avisos `400/405/415` | ✅ PASSOU |

**Pendentes:** E-02 (cabeçalho `Server` do nginx, exige autorização de infraestrutura) e E-03 (visual da tela "Acesso Restrito"), além das observações E-04 a E-08 e das limitações da seção 8.
