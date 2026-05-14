# Guia de Deploy - PortalCursos.NG (Hostinger VPS)

Este guia descreve como utilizar o script automatizado para implantar o ecossistema PortalCursos.NG em uma VPS Ubuntu na Hostinger.

## 📋 Pré-requisitos
1. **Acesso SSH:** Você deve ter acesso root ou usuário com permissões sudo.
2. **Sistema Operacional:** Ubuntu 22.04 LTS ou superior (recomendado).
3. **Domínio:** Ter um domínio ou subdomínio apontando para o IP da VPS.
4. **Banco de Dados:** Ter as credenciais do banco Neon (PostgreSQL) prontas.

## 🛠️ O que o script faz
- Instala **JDK 17** (Backend), **Node.js 20** (Frontend), **Maven** (Build) e **PM2/Nginx**.
- Configura o Firewall (UFW) e permissões de pasta.
- Cria o arquivo `.env` com placeholders para suas credenciais.
- Configura o Nginx como Proxy Reverso.

## 🚀 Como usar
1. Copie o script `hostinger_deploy.sh` para sua VPS.
2. Dê permissão de execução: `chmod +x hostinger_deploy.sh`.
3. Execute o script: `./hostinger_deploy.sh`.
4. Siga as instruções no console para preencher os placeholders no arquivo `.env` gerado.

## 🔒 Segurança
- O script configura o Firewall para permitir apenas portas essenciais (SSH, HTTP, HTTPS).
- Utiliza o PM2 para garantir que o sistema reinicie automaticamente em caso de falhas.
