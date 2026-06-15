# =================================================================
# deploy_github_to_vps.ps1
# PortalCursos.NG — Deploy completo: Antigravity → GitHub → VPS
#
# Como usar:
#   cd C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG
#   .\devops\scripts\deploy_github_to_vps.ps1
#
# Pré-requisitos:
#   - Git instalado e configurado (git config user.email / user.name)
#   - Acesso SSH configurado para a VPS (chave ou senha)
#   - Variáveis de ambiente opcionais: HOSTINGER_IP, HOSTINGER_SSH_KEY_PATH
# =================================================================

$ErrorActionPreference = "Stop"

# ── Configuração ─────────────────────────────────────────────────
$ProjectRoot  = "C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG"
$GitHubRepo   = "https://github.com/claudiofxbr/PortalCursos.NG"
$GitBranch    = "main"
$VpsUser      = "root"
$VpsDir       = "/var/www/portalcursos"
$AppUrl       = "https://xavierbr-vps.tech/portalcursos.ng"
$ApiHealthUrl = "https://xavierbr-vps.tech/api/health"

# ── Helpers ──────────────────────────────────────────────────────
function Write-Step  { param($n, $msg) Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Write-Ok    { param($msg)     Write-Host "    OK  $msg" -ForegroundColor Green }
function Write-Warn  { param($msg)     Write-Host "    AVISO  $msg" -ForegroundColor Yellow }
function Write-Fail  { param($msg)     Write-Host "    ERRO  $msg" -ForegroundColor Red; exit 1 }

# ─────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host "  PORTALCURSOS.NG — Deploy Antigravity → GitHub → VPS Hostinger" -ForegroundColor Magenta
Write-Host "  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "=================================================================" -ForegroundColor Magenta

# ── FASE 1: Verificar pré-requisitos locais ───────────────────────
Write-Step "1/5" "Verificando pré-requisitos locais"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Fail "Git não encontrado. Instale em: https://git-scm.com/download/win"
}
Write-Ok "Git: $(git --version)"

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    Write-Fail "SSH não encontrado. Habilite em: Configurações → Recursos opcionais → Cliente OpenSSH"
}
Write-Ok "SSH disponível"

if (-not (Test-Path $ProjectRoot)) {
    Write-Fail "Diretório do projeto não encontrado: $ProjectRoot"
}
Set-Location $ProjectRoot
Write-Ok "Diretório: $ProjectRoot"

# ── FASE 2: Commit e push para o GitHub ──────────────────────────
Write-Step "2/5" "Enviando código ao GitHub ($GitHubRepo)"

$gitStatus = git status --porcelain 2>&1
if ($gitStatus) {
    Write-Host "    Arquivos alterados detectados:" -ForegroundColor Yellow
    $gitStatus | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }

    $commitMsg = Read-Host "`n    Mensagem do commit (Enter para usar padrão)"
    if (-not $commitMsg) {
        $commitMsg = "deploy: atualizacao portalcursos.ng $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }

    git add .
    if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git add" }

    git commit -m $commitMsg
    if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git commit" }

    Write-Ok "Commit criado: $commitMsg"
} else {
    Write-Ok "Nenhuma alteração local — usando commit atual"
}

Write-Host "    Enviando para GitHub..." -ForegroundColor DarkGray
git push origin $GitBranch
if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git push. Verifique autenticação com o GitHub." }

$lastCommit = git log --oneline -1
Write-Ok "Push realizado: $lastCommit"

# ── FASE 3: Verificar se GitHub Actions vai rodar ────────────────
Write-Step "3/5" "GitHub Actions"
Write-Host "    O GitHub Actions iniciará o deploy automaticamente." -ForegroundColor DarkGray
Write-Host "    Acompanhe em: $GitHubRepo/actions" -ForegroundColor DarkGray
Write-Host ""

$skipManual = Read-Host "    O GitHub Actions está configurado com os 3 Secrets? (s/N)"
if ($skipManual -match '^[sS]$') {
    Write-Ok "Deploy via GitHub Actions iniciado."
    Write-Host ""
    Write-Host "    Aguarde ~5 minutos e acesse:" -ForegroundColor Yellow
    Write-Host "    Frontend: $AppUrl" -ForegroundColor Green
    Write-Host "    API:      $ApiHealthUrl" -ForegroundColor Green
    Write-Host ""
    Write-Host "    Se quiser verificar agora, rode:" -ForegroundColor DarkGray
    Write-Host "    .\devops\scripts\diag_vps.ps1" -ForegroundColor DarkGray
    exit 0
}

# ── FASE 4: Deploy manual na VPS (quando Actions não está pronto) ─
Write-Step "4/5" "Deploy manual na VPS via SSH"

$VpsIp = $env:HOSTINGER_IP
if (-not $VpsIp) {
    $VpsIp = Read-Host "    Digite o IP da VPS Hostinger (ex: 185.241.52.100)"
}
if (-not $VpsIp) { Write-Fail "IP da VPS não informado." }
Write-Ok "VPS: ${VpsUser}@${VpsIp}"

$SshOpts = @(
    "-o", "StrictHostKeyChecking=accept-new",
    "-o", "ConnectTimeout=30",
    "-o", "ServerAliveInterval=60",
    "-o", "ServerAliveCountMax=10"
)

$SshKeyPath = $env:HOSTINGER_SSH_KEY_PATH
if ($SshKeyPath -and (Test-Path $SshKeyPath)) {
    $SshOpts += @("-i", $SshKeyPath)
    Write-Ok "Chave SSH: $SshKeyPath"
} else {
    Write-Warn "HOSTINGER_SSH_KEY_PATH não definido — usando autenticação por senha"
}

# Script remoto executado na VPS
$RemoteScript = @"
set -euo pipefail

echo ""
echo "=== [VPS] Iniciando deploy manual ==="
echo "    Servidor : \$(hostname)"
echo "    Data     : \$(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Verificar se o diretório do projeto existe
if [ ! -d "$VpsDir" ]; then
    echo "[SETUP] Primeira vez — clonando repositório..."
    mkdir -p "$VpsDir"
    git clone $GitHubRepo "$VpsDir"
    echo "[OK] Repositório clonado"
fi

cd "$VpsDir"

# Verificar .env
if [ ! -f ".env" ]; then
    echo ""
    echo "[ERRO] Arquivo .env nao encontrado em $VpsDir/.env"
    echo "       Crie o arquivo com as variaveis obrigatorias:"
    echo ""
    echo "  APP_JWT_SECRET=<minimo-64-caracteres>"
    echo "  SPRING_DATASOURCE_URL=jdbc:postgresql://<host-neon>/neondb?sslmode=require"
    echo "  SPRING_DATASOURCE_USERNAME=<usuario>"
    echo "  SPRING_DATASOURCE_PASSWORD=<senha>"
    echo "  CORS_ALLOWED_ORIGINS=https://xavierbr-vps.tech"
    echo "  DOMAIN_NAME=xavierbr-vps.tech"
    echo "  NEXT_PUBLIC_API_URL=https://xavierbr-vps.tech"
    echo "  NEXT_PUBLIC_BASE_PATH=/portalcursos.ng"
    echo "  APP_SECURE_COOKIES=true"
    echo ""
    exit 1
fi

# Garantir rede Traefik
docker network create easypanel 2>/dev/null || true
echo "[OK] Rede easypanel verificada"

# Sincronizar código
echo "[SYNC] Atualizando código do GitHub..."
git fetch origin $GitBranch --depth=1
git reset --hard FETCH_HEAD
echo "[OK] Codigo: \$(git log --oneline -1)"

# Build e deploy
echo "[BUILD] Construindo imagens Docker..."
docker compose -f devops/docker-compose.prod.yml --env-file .env build backend frontend

echo "[UP] Subindo containers..."
docker compose -f devops/docker-compose.prod.yml --env-file .env up -d --force-recreate backend frontend
docker compose -f devops/docker-compose.prod.yml --env-file .env up -d postgres

# Aguardar backend
echo "[WAIT] Aguardando backend (max 60s)..."
for i in \$(seq 1 30); do
    if docker exec portalcursos_backend wget -qO- http://localhost:8080/api/health 2>/dev/null | grep -q '"status":"UP"'; then
        echo "[OK] Backend saudavel"
        break
    fi
    [ "\$i" -eq 30 ] && echo "[AVISO] Backend nao respondeu em 60s" && docker logs portalcursos_backend --tail 20
    sleep 2
done

# Reconectar à rede Traefik
for c in portalcursos_backend portalcursos_frontend; do
    docker network connect easypanel "\$c" 2>/dev/null && echo "[OK] \$c conectado ao Traefik" || echo "[OK] \$c ja estava conectado"
done

# Reload Traefik
TRAEFIK_ID=\$(docker ps --filter name=easypanel-traefik -q | head -1)
[ -n "\$TRAEFIK_ID" ] && docker kill --signal=SIGHUP "\$TRAEFIK_ID" && echo "[OK] Traefik recarregado"

echo ""
echo "============================================"
echo "  Deploy concluido!"
echo "  Frontend : $AppUrl"
echo "  API      : $ApiHealthUrl"
echo "============================================"
"@

Write-Host "    Executando deploy na VPS..." -ForegroundColor Yellow
$SshArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteScript)
& ssh @SshArgs
$ExitCode = $LASTEXITCODE

if ($ExitCode -ne 0) {
    Write-Host ""
    Write-Warn "Deploy terminou com erro (código $ExitCode)."
    Write-Host "    Para diagnosticar, rode:" -ForegroundColor DarkGray
    Write-Host "    .\devops\scripts\diag_502.ps1" -ForegroundColor DarkGray
    exit $ExitCode
}

# ── FASE 5: Verificação final ─────────────────────────────────────
Write-Step "5/5" "Verificação final"
Write-Host "    Aguardando 5s para estabilizar..." -ForegroundColor DarkGray
Start-Sleep -Seconds 5

try {
    $resp = Invoke-WebRequest -Uri $ApiHealthUrl -UseBasicParsing -TimeoutSec 15 -SkipCertificateCheck 2>$null
    if ($resp.StatusCode -eq 200 -and $resp.Content -match '"status":"UP"') {
        Write-Ok "API health: UP (HTTP $($resp.StatusCode))"
    } else {
        Write-Warn "API retornou HTTP $($resp.StatusCode) — verifique os logs"
    }
} catch {
    Write-Warn "Não foi possível verificar a API externamente (DNS pode levar alguns minutos)"
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host "  Deploy finalizado." -ForegroundColor Green
Write-Host "  Acesse: $AppUrl" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host ""
