# Relatório de análise — botão "Gerar relatório de análise"

## O botão
- **Rótulo:** `Gerar relatório de análise` (botão de destaque, com `aria-haspopup="dialog"`).
- **Posição:** cabeçalho da página, à direita, **antes** de "Configurar plano" e "Atualizar". Fica visível em **qualquer aba** (Semana, Mês, Torre).
- **Ação (um clique):** busca `GET /api/tokens/report` e abre o painel **Relatório de análise** (modal) com o texto em português.
- **No painel:** `Imprimir / salvar em PDF` (impressão do navegador, só o relatório), `Baixar (.md)` (`relatorio-controle-de-tokens-AAAA-MM-DD.md`, data no fuso configurado) e `Fechar` (também `Esc` e clique fora). Em caso de erro: mensagem + `Tentar novamente`.

## O que o relatório analisa
"Análise do aplicativo" = **uso e operação** do app (não auditoria de código). Seis seções, todas calculadas dos mesmos serviços do dashboard e da Torre (números sempre iguais entre as telas):
1. **Resumo executivo** — estado geral da operação, semana, mês, dados analisados e último envio.
2. **Ciclo semanal** e 3. **Mês atual** — uso × limite, ritmo no tempo (adiantado / alinhado / abaixo do ritmo linear), projeção até o fim (e quando esgota), **ritmo máximo por dia** para fechar dentro do orçamento, dia mais intenso.
4. **Quem consome (mês)** — tabela por processo (top 8), modelos e composição bruta dos tokens (entrada, saída, criação e leitura de cache).
5. **Pontos de atenção** — itens críticos, em atenção e na fila da Torre, por gravidade.
6. **Recomendações** — ações concretas, só quando se aplicam: sem consumo registrado, ritmo acima do orçamento (com o máximo permitido por dia), processo que concentra ≥ 50% do consumo, leitura de cache ≥ 80% do volume bruto (fora da conta), coletor desatualizado, limite semanal provisório, orçamento mensal indefinido, API do Neon, banco com problema. Sem nada a fazer: "Nenhuma ação necessária no momento".

## Regras e restrições
- Somente leitura; gerado sob demanda (sem armazenar relatórios, sem tabelas novas); mesmo login/chave do app; sem texto de conversa.
- As recomendações são **regras determinísticas** sobre os dados (não usam IA). Refletem o limite **configurado**, não o limite real do plano (que a Anthropic não publica).
- O PDF é gerado pelo diálogo de impressão do navegador ("Salvar como PDF").
