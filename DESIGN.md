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

## 5. Requisitos Não Funcionais
*   **Escalabilidade:** Suporte a 1000+ usuários ativos.
*   **Auditoria:** Registro de logs de todas as alterações críticas (financeiras e cadastrais).
*   **UX/UI:** Interface minimalista e premium.
