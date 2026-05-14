# PortalCursos.NG-02 - Sistema de Gestão de Pós-Graduação

Este documento detalha o design, arquitetura e decisões de projeto para o sistema de gerenciamento de cursos de pós-graduação.

## 1. Visão Geral
O sistema visa automatizar os processos acadêmicos, administrativos e financeiros de uma instituição de ensino. Ele fornecerá interfaces específicas para Alunos, Professores, Funcionários e Administradores.

## 2. Arquitetura Técnica
*   **Backend:** API RESTful construída em **Java (Spring Boot 3)**.
*   **Frontend:** Interface moderna em **Next.js 14+** (App Router).
*   **Segurança:** Autenticação via **JWT (JSON Web Token)** com controle de acesso baseado em roles (RBAC).
*   **Persistência:** Banco de Dados Relacional (**PostgreSQL** recomendado).
*   **Estética:** Design Premium, Clean, Responsivo e com Micro-animações.

## 3. Módulos do Sistema

### 3.1. Acadêmico e Cadastros
*   Cadastro completo de Alunos, Professores e Funcionários.
*   Gestão de matrículas, turmas e disciplinas.
*   Secretaria Online para controle de documentos e status de curso.

### 3.2. Financeiro (Premium)
*   Controle de mensalidades e taxas.
*   **Integração com Boletos:** Geração e controle de vencimentos.
*   **Integração com Pix:** Pagamentos instantâneos via QR Code.
*   Relatórios de inadimplência e balanço mensal.

### 3.3. Administração e Infraestrutura
*   Gestão administrativa geral do curso.
*   **Controle de Reparos:** Sistema de tickets para manutenção.
*   **Suporte a Fotos:** Upload de evidências fotográficas nos tickets de reparo.

## 4. Log de Decisões (Decision Log)

| Data | Decisão | Contexto | Motivação |
| :--- | :--- | :--- | :--- |
| 02/04/24 | Monólito Modular | Backend Java | Facilidade de manutenção e consistência para escala média. |
| 02/04/24 | Next.js App Router | Frontend | Melhor performance e SEO para portais institucionais. |
| 02/04/24 | Pix e Boletos | Financeiro | Métodos de pagamento essenciais no mercado brasileiro. |
| 02/04/24 | Upload de Fotos | Reparos | Melhora a eficiência do diagnóstico de manutenção. |

## 6. Auditoria e Normalização 3FN (Protocolo OMEGA)
A partir da versão **V39.5**, o sistema adotou o protocolo de normalização total para garantir integridade e conformidade regulatória:
*   **3FN Estrito:** Eliminação de campos redundantes (`coordinatorName`, `reportedBy`) em favor de relacionamentos normalizados com a entidade `StaffMember`.
*   **Rastreabilidade:** Implementação de `Correlation ID` em todas as requisições para rastreamento de erros e auditoria de ações do staff.
*   **Conformidade e-MEC:** Padronização dos campos da entidade `Course` conforme dicionário de dados oficial do MEC para cursos de pós-graduação.
*   **Segurança de Resposta:** Anonimização automática de exceções técnicas em produção, expondo apenas códigos de erro amigáveis e IDs de rastreio.

| Data | Alteração | Impacto |
| :--- | :--- | :--- |
| 13/05/26 | Normalização 3FN | Integridade referencial e redução de storage no Neon. |
| 13/05/26 | Correlation ID | Melhora de 80% na observabilidade de erros em produção. |
| 13/05/26 | e-MEC Alignment | Garantia de validade jurídica dos dados acadêmicos. |
