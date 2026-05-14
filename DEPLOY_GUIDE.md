# 🚀 Guia de Implantação de Produção - PortalCursos.NG

Este guia técnico detalha o processo de deploy completo para a VPS **xavierbr-VPS** (Hostinger), garantindo alta disponibilidade, segurança e performance.

---

## 1. Configuração do Repositório (Local)

Antes de enviar para a VPS, garanta que seu código está versionado corretamente.

### Inicialização e Organização
1.  Abra o terminal na pasta raiz do projeto.
2.  Inicialize o Git (se ainda não o fez):
    ```powershell
    git init
    ```
3.  Garanta que o `.gitignore` está configurado para ignorar `node_modules`, `target/` e arquivos `.env`.
4.  Crie o repositório no GitHub (ex: `xavierbr/PortalCursos.NG`).
5.  Adicione os arquivos e realize o commit inicial:
    ```powershell
    git add .
    git commit -m "feat: OMEGA-SUPREME production ready with security gate"
    ```
6.  Vincule ao GitHub e faça o push:
    ```powershell
    git remote add origin https://github.com/SEU_USUARIO/PortalCursos.NG.git
    git branch -M main
    git push -u origin main
    ```

---

## 2. Preparação do Servidor (xavierbr-VPS)

Conecte-se à sua VPS via SSH e instale os componentes base.

### Requisitos de Ambiente
Execute os comandos abaixo para preparar o ambiente Ubuntu/Debian:

```bash
# Atualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar Java 17 (Backend Spring Boot)
sudo apt install openjdk-17-jdk -y

# Instalar Node.js 20 (Frontend Next.js)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Instalar Servidor Web e Gerenciador de Processos
sudo apt install nginx -y
sudo npm install -g pm2
```

---

## 3. Processo de Implantação

Com o servidor pronto, vamos clonar e buildar a aplicação.

### Clonagem e Dependências
```bash
# Clonar o projeto
git clone https://github.com/SEU_USUARIO/PortalCursos.NG.git /var/www/portalcursos
cd /var/www/portalcursos

# Configurar Variáveis de Ambiente
cp .env.example .env
nano .env  # Insira as credenciais reais do Neon PostgreSQL
```

### Build e Inicialização OMEGA
O projeto conta com um script automatizado de deploy. Execute-o:
```bash
chmod +x devops/scripts/deploy_omega.sh
./devops/scripts/deploy_omega.sh
```
*Este script realiza o `./mvnw install` no backend e `npm run build` no frontend.*

---

## 4. Segurança e Acesso

### Camada de Autenticação Global (7512)
Implementamos uma barreira de segurança no nível de aplicação (`SecurityGate.tsx`). 
- **Lógica**: O sistema verifica um token no `sessionStorage`. 
- **Senha**: `7512`.
- **Funcionamento**: Qualquer tentativa de acesso ao domínio xavierbr-VPS exibirá uma tela de bloqueio antes de carregar o PortalCursos.

### Configuração do Nginx (Proxy Reverso)
Crie o arquivo de configuração: `sudo nano /etc/nginx/sites-available/portalcursos`
```nginx
server {
    listen 80;
    server_name xavierbr-vps.com; # Substitua pelo seu IP ou domínio real

    location / {
        proxy_pass http://localhost:3000; # Frontend Next.js
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /api {
        proxy_pass http://localhost:8080; # Backend Spring Boot
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```
Ative e reinicie:
```bash
sudo ln -s /etc/nginx/sites-available/portalcursos /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

## 5. Validação e Testes

### Comandos de Verificação
- **DNS/Conexão**: Verifique se o domínio aponta para o IP `69.62.87.38`:
    ```bash
    ping seu-dominio.com
    ```
- **Processos**: Garanta que PM2 está gerenciando os serviços:
    ```bash
    pm2 list
    ```
- **Teste de Carga Simples**: Verifique se o Nginx aguenta conexões simultâneas:
    ```bash
    ab -n 100 -c 10 http://seu-dominio.com/
    ```

### Checklist de Funcionamento
1. [ ] Acessar o domínio e verificar se a tela de "Acesso Restrito" aparece.
2. [ ] Digitar a senha `7512` e validar se o sistema libera o acesso.
3. [ ] Testar uma rota de API (ex: `/api/v1/courses`) para validar o proxy do backend.
4. [ ] Verificar logs de erro: `pm2 logs portalcursos-frontend`.

---
*Documentação Gerada por Antigravity OMEGA - 2026*
