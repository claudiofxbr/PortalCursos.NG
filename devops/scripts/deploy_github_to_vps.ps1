#Requires -Version 7.0
# =================================================================
# deploy_github_to_vps.ps1
# PortalCursos.NG — Deploy completo: Antigravity → GitHub → VPS
#
# Como usar (requer PowerShell 7+ / pwsh):
#   cd "C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG"
#   pwsh .\devops\scripts\deploy_github_to_vps.ps1
#
# ATENCAO: NAO execute com "powershell" (v5). Use "pwsh" (v7+).
#
# Pre-requisitos:
#   - Git instalado
#   - SSH disponivel (Cliente OpenSSH do Windows)
#   - Variaveis opcionais: $env:HOSTINGER_IP, $env:HOSTINGER_SSH_KEY_PATH
# =================================================================

$ErrorActionPreference = "Stop"

# ── Configuracao ──────────────────────────────────────────────────
$ProjectRoot  = "C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG"
$GitHubRepo   = "https://github.com/claudiofxbr/PortalCursos.NG"
$GitBranch    = "main"
$VpsUser      = "root"
$AppUrl       = "https://xavierbr-vps.tech/portalcursos.ng"
$ApiHealthUrl = "https://xavierbr-vps.tech/api/health"
$LocalScript  = Join-Path $PSScriptRoot "deploy_vps.sh"

# ── Helpers ───────────────────────────────────────────────────────
function Write-Step { param($n, $msg) Write-Host "" ; Write-Host "[$n] $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg)     Write-Host "    OK    $msg" -ForegroundColor Green }
function Write-Warn { param($msg)     Write-Host "    AVISO $msg" -ForegroundColor Yellow }
function Write-Fail { param($msg)     Write-Host "    ERRO  $msg" -ForegroundColor Red ; exit 1 }

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host "  PORTALCURSOS.NG - Deploy Antigravity > GitHub > VPS Hostinger" -ForegroundColor Magenta
Write-Host "  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"                      -ForegroundColor DarkGray
Write-Host "=================================================================" -ForegroundColor Magenta

# ── FASE 1: Pre-requisitos locais ─────────────────────────────────
Write-Step "1/5" "Verificando pre-requisitos locais"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Fail "Git nao encontrado. Instale em: https://git-scm.com/download/win"
}
Write-Ok "Git: $(git --version)"

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    Write-Fail "SSH nao encontrado. Habilite: Configuracoes > Recursos opcionais > Cliente OpenSSH"
}
Write-Ok "SSH disponivel"

if (-not (Test-Path $ProjectRoot)) {
    Write-Fail "Diretorio do projeto nao encontrado: $ProjectRoot"
}
Set-Location $ProjectRoot
Write-Ok "Diretorio: $ProjectRoot"

if (-not (Test-Path $LocalScript)) {
    Write-Fail "Script de deploy nao encontrado: $LocalScript"
}
Write-Ok "Script de deploy: $LocalScript"

# ── FASE 2: Commit e push para o GitHub ───────────────────────────
Write-Step "2/5" "Enviando codigo ao GitHub"

$gitStatus = git status --porcelain 2>&1
if ($gitStatus) {
    Write-Host "    Arquivos alterados:" -ForegroundColor Yellow
    $gitStatus | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }

    $commitMsg = Read-Host "    Mensagem do commit (Enter para usar padrao)"
    if (-not $commitMsg) {
        $commitMsg = "deploy: atualizacao portalcursos.ng $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }

    git add .
    if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git add" }

    git commit -m $commitMsg
    if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git commit" }

    Write-Ok "Commit: $commitMsg"
} else {
    Write-Ok "Nenhuma alteracao local — commit atual sera usado"
}

Write-Host "    Enviando para GitHub..." -ForegroundColor DarkGray
git push origin $GitBranch
if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git push. Verifique autenticacao com o GitHub." }

$lastCommit = git log --oneline -1
Write-Ok "Push realizado: $lastCommit"

# ── FASE 3: GitHub Actions ────────────────────────────────────────
Write-Step "3/5" "GitHub Actions"
Write-Host "    Deploy automatico iniciara em: $GitHubRepo/actions" -ForegroundColor DarkGray
Write-Host ""

$useActions = Read-Host "    O GitHub Actions esta configurado com os 3 Secrets? (s/N)"
if ($useActions -match '^[sS]$') {
    Write-Ok "Deploy via GitHub Actions em andamento."
    Write-Host ""
    Write-Host "    Aguarde ~8 minutos e acesse:" -ForegroundColor Yellow
    Write-Host "    $AppUrl" -ForegroundColor Green
    Write-Host ""
    Write-Host "    Para monitorar: $GitHubRepo/actions" -ForegroundColor DarkGray
    Write-Host "    Para diagnosticar 502: .\devops\scripts\diag_vps.ps1" -ForegroundColor DarkGray
    exit 0
}

# ── FASE 4: Deploy manual via SSH ─────────────────────────────────
Write-Step "4/5" "Deploy manual na VPS via SSH"

$VpsIp = $env:HOSTINGER_IP
if (-not $VpsIp) {
    $VpsIp = Read-Host "    Digite o IP da VPS Hostinger (ex: 185.241.52.100)"
}
if (-not $VpsIp) { Write-Fail "IP da VPS nao informado." }
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
    Write-Warn "HOSTINGER_SSH_KEY_PATH nao definido — sera solicitada senha"
}

Write-Host "    [1/2] Enviando deploy_vps.sh para a VPS..." -ForegroundColor Yellow
$ScpArgs = $SshOpts + @($LocalScript, "${VpsUser}@${VpsIp}:/tmp/deploy_vps.sh")
& scp @ScpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Falha no SCP. Verifique acesso SSH: ssh ${VpsUser}@${VpsIp}"
}
Write-Ok "Script enviado para /tmp/deploy_vps.sh"

Write-Host "    [2/2] Executando deploy na VPS (pode demorar 8-12 minutos)..." -ForegroundColor Yellow
Write-Host "          O build Docker do frontend leva mais tempo." -ForegroundColor DarkGray
Write-Host ""

$RemoteCmd = "chmod +x /tmp/deploy_vps.sh; bash /tmp/deploy_vps.sh 2>&1"
$SshRunArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteCmd)
& ssh @SshRunArgs
$ExitCode = $LASTEXITCODE

if ($ExitCode -ne 0) {
    Write-Host ""
    Write-Warn "Deploy terminou com erro (codigo $ExitCode)."
    Write-Host "    Para diagnosticar:" -ForegroundColor DarkGray
    Write-Host "      .\devops\scripts\diag_vps.ps1" -ForegroundColor DarkGray
    Write-Host "      .\devops\scripts\fix_502.ps1" -ForegroundColor DarkGray
    exit $ExitCode
}

# ── FASE 5: Verificacao final ─────────────────────────────────────
Write-Step "5/5" "Verificacao final"
Write-Host "    Aguardando 5s para estabilizar..." -ForegroundColor DarkGray
Start-Sleep -Seconds 5

try {
    $resp = Invoke-WebRequest -Uri $ApiHealthUrl -UseBasicParsing -TimeoutSec 15 -SkipCertificateCheck
    if ($resp.StatusCode -eq 200 -and $resp.Content -match '"status":"UP"') {
        Write-Ok "API health: UP (HTTP $($resp.StatusCode))"
    } else {
        Write-Warn "API retornou HTTP $($resp.StatusCode) — verifique os logs"
    }
} catch {
    Write-Warn "Nao foi possivel verificar a API agora (DNS pode levar alguns minutos)"
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host "  Deploy finalizado." -ForegroundColor Green
Write-Host "  Acesse: $AppUrl" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host ""
