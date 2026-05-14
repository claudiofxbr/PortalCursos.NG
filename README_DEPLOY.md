# Guia de Deploy: PortalCursos.NG (Hostinger + Neon)

Este documento descreve o processo robusto para realizar o deploy da aplicação em ambiente de produção.

## 1. Banco de Dados (Neon)
A aplicação utiliza o **Flyway** para gerenciar o esquema automaticamente.
- **URL**: `jdbc:postgresql://<host>/neondb?sslmode=require`
- **Migrações**: Ao iniciar o backend, o Flyway aplicará o script `V1__Initial_Schema.sql` localizado em `resources/db/migration`.
- **Atenção**: Não execute scripts SQL manuais no console do Neon a menos que seja estritamente necessário. Use migrações `V2__...`, `V3__...` para novas mudanças.

## 2. Deploy na Hostinger (VPS)
Recomendamos o uso de **Docker** para isolar o ambiente.

### Pré-requisitos na VPS:
- Docker e Docker Compose instalados.
- Porta 8080 (Backend) e 3000 (Frontend) abertas no firewall.

### Estrutura de Diretórios Recomendada:
```
/home/usuario/portal-cursos/
├── backend/
│   └── Dockerfile
├── frontend/
│   └── Dockerfile
└── docker-compose.yml
```

### Variáveis de Ambiente Necessárias:
Crie um arquivo `.env` na raiz da VPS:
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-xxx.neon.tech/neondb
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=sua_senha_neon
APP_ROOT_PASSWORD=senha_secreta_root
APP_ADMIN_PASSWORD=senha_secreta_admin
NEXT_PUBLIC_API_URL=http://seu-ip-ou-dominio:8080
```

## 3. Pipeline de Implementação (GitHub Actions)
O fluxo já está configurado em `.github/workflows/deploy.yml`.
Para ativar o deploy automático:
1. Vá em seu repositório no GitHub: **Settings > Secrets and variables > Actions**.
2. Adicione os seguintes Secrets:
   - `HOSTINGER_SSH_KEY`: Sua chave privada SSH.
   - `HOSTINGER_IP`: IP da sua VPS na Hostinger.
   - `HOSTINGER_USER`: Usuário da VPS (ex: root ou ubuntu).

## 4. Tratamento de Erros
- **Conexão com Banco**: O `DatabaseResilienceComponent` aguardará até 150 segundos para o Neon "acordar". Se falhar, verifique se o IP da Hostinger está autorizado no painel do Neon.
- **Build do Next.js**: Se o build falhar por memória, aumente o swap da VPS ou use `GENERATE_SOURCEMAP=false`.

## 5. Scripts de Automação (DevOps)
Para facilitar a gestão manual, foram criados scripts na pasta `/devops/scripts/`:

| Script | Função |
| :--- | :--- |
| `setup_server.sh` | Prepara o servidor (Instala Java 17, Node 18, PM2, Nginx). |
| `deploy_backend.sh` | Compila o Java (JAR: `portalcursos-backend.jar`), limpa porta 8080 e reinicia no PM2. |
| `deploy_frontend.sh` | Builda o Next.js e reinicia o frontend no PM2. |
| `nginx.conf` | Template de Proxy Reverso para unificar acesso na porta 80. |

**Como usar:**
1. Dê permissão de execução: `chmod +x devops/scripts/*.sh`
2. Execute o setup: `./devops/scripts/setup_server.sh`
3. Configure o Nginx usando o template `nginx.conf`.
4. Execute o deploy do backend: `./devops/scripts/deploy_backend.sh`

## 6. Observações OMEGA-ULTRA
- **JAR Name**: O backend agora gera sempre `portalcursos-backend.jar` para evitar erros de path no PM2.
- **Port Reset**: O script de deploy agora mata processos na porta 8080 automaticamente.
- **Neon Timeout**: O Nginx foi configurado com timeout de 150s para suportar o cold start do Neon.

