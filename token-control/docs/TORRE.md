# Torre de Controle dos Processos — Controle de Tokens Claude Code

Aba **Torre de Controle** do app (além de *Semana (ciclo)* e *Mês atual*) e selo **"Torre: …"** no cabeçalho, visível em qualquer aba.

## 1. Objetivo principal
Dar **uma visão única, em tempo real e somente leitura** de (a) como estão os processos que mantêm o app funcionando — coleta, API, banco, ciclo semanal e mês — e (b) **quais processos (projetos/subagentes) estão consumindo tokens agora**, apontando o que exige ação do usuário. Responde, em segundos: *"está tudo funcionando e dentro do orçamento? o que precisa de mim?"*.

## 2. Funcionalidades essenciais
- **Estado geral** (`Operação normal` / `Atenção` / `Crítico`) = pior estado entre os itens (a fila de pendências não degrada o estado).
- Quatro seções, no padrão das Torres do projeto:
  - **Em andamento:** processos que consumiram tokens nas **últimas 5 h** (até 8, por consumo), com tokens, mensagens e última atividade.
  - **Fila / pendências:** alertas de **projeção que estoura** o orçamento (semana/mês) e pendências de configuração (limite semanal provisório, orçamento mensal não definido, API do Neon desligada ou com erro).
  - **Saúde do sistema:** API e banco Neon (conexão, latência, migrations, pooled/direto).
  - **Processos automáticos recorrentes (sempre visíveis):** coletor de consumo (idade do último envio), ciclo semanal e mês atual (uso × limite, tempo até o reset/fim do mês).
- Cada item tem **status em texto + ícone** (ok, ativo, na fila, atenção, crítico) — nunca só cor.
- **Limiares:** orçamento ≥ 70% = atenção, ≥ 90% = crítico (os mesmos do medidor do dashboard); coletor: último envio < 6 h ok, < 48 h atenção, ≥ 48 h crítico; latência do banco > 1 s = atenção; banco desconectado ou migration com falha = crítico.

## 3. Integração com as demais áreas
- Calculada no backend (`GET /api/tokens/tower`) **reutilizando** os serviços existentes: `SummaryService` (semana e mês), `DbStatusService` (banco e API do Neon), `ConfigService` (limites) e `UsageRepository` (coleta e atividade). **Sem tabelas novas e sem migration.**
- No frontend: aba própria, selo no cabeçalho (clique abre a Torre), atualização junto do restante (a cada 60 s) e BFF com a rota na *allowlist* (a chave do backend continua só no servidor).
- Os alertas apontam para as outras áreas ("Configurar plano", `enviar-consumo.ps1`, variáveis do Neon).
- Falha da Torre não derruba as demais abas (e vice-versa): ela é buscada em chamada separada.

## 4. Regras de uso e comportamento
- Só mostra o que está **ativo, pendente ou em alerta**; recorrentes ficam sempre visíveis; itens resolvidos somem sozinhos (o cálculo é sempre sobre o estado atual).
- Somente leitura: a Torre **não executa ações** (não reenvia dados, não altera configuração).
- Protegida pelo mesmo login e chave do app. Sem texto de conversa: só contagens e metadados.

## 5. Restrições
- A Torre vê apenas o que o **banco do app** conhece. Se o collector não rodar, o app não sabe quanto o Claude Code consumiu — por isso o item *Coletor* envelhece e alerta.
- Não detecta falhas do CI/deploy nem de outros apps da VPS (isso fica nas Torres do `claude.ai`, que não conseguem ler este app: o ambiente cloud bloqueia o servidor).
- O limite real do plano não é publicado pela Anthropic: os alertas de orçamento valem para o valor **configurado**.

## 6. Critérios de sucesso
- `GET /api/tokens/tower` responde em < 1 s com banco local e cobre todas as regras acima (**10 testes unitários** + integração + frontend + E2E).
- Banco fora do ar → estado **Crítico** em vez de erro; coletor parado → **Atenção/Crítico** com a instrução de correção; projeção que estoura aparece na fila.
- Com tudo certo e sem pendências → **Operação normal**.

## Suposições adotadas (ambiguidades)
1. **Nome do app:** mantido **"Controle de Tokens Claude Code"** (nome original e título atual). "Claude Coude Pro" foi lido como erro de digitação e **não** foi replicado; "Claude Code Pro" aparece só como nome do plano na configuração.
2. **"Processos"** = os processos do próprio app **mais** os projetos/subagentes que consomem tokens (o app não conhece CI/deploy).
3. **Torre dentro do app**, e não um artifact externo, porque o artifact não alcança o app.
