# fix_502.ps1 - Corrige 502 Bad Gateway na VPS Hostinger
# PortalCursos.NG | xavierbr-vps.tech
#
# Como usar:
#   cd C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG
#   .\devops\scripts\fix_502.ps1

$ErrorActionPreference = "Stop"

$VpsUser     = "root"
$LocalScript = Join-Path $PSScriptRoot "fix_502.sh"

$VpsIp = $env:HOSTINGER_IP
if (-not $VpsIp) {
    $VpsIp = Read-Host "Digite o IP da VPS Hostinger (ex: 185.xxx.xxx.xxx)"
}
if (-not $VpsIp) {
    Write-Host "ERRO: IP da VPS nao informado." -ForegroundColor Red
    exit 1
}

$SshKeyPath = $env:HOSTINGER_SSH_KEY_PATH

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  FIX 502 Bad Gateway - PortalCursos.NG" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  VPS: ${VpsUser}@${VpsIp}" -ForegroundColor Yellow
Write-Host ""

if (-not (Test-Path $LocalScript)) {
    Write-Host "ERRO: fix_502.sh nao encontrado em: $LocalScript" -ForegroundColor Red
    exit 1
}

$SshOpts = @(
    "-o", "StrictHostKeyChecking=accept-new",
    "-o", "ConnectTimeout=30",
    "-o", "ServerAliveInterval=60"
)

if ($SshKeyPath -and (Test-Path $SshKeyPath)) {
    $SshOpts += @("-i", $SshKeyPath)
    Write-Host "  Chave SSH: $SshKeyPath" -ForegroundColor DarkGray
}

# --- PASSO 1: Envia o script ---
Write-Host "[1/2] Enviando fix_502.sh para a VPS..." -ForegroundColor Yellow

$ScpArgs = $SshOpts + @($LocalScript, "${VpsUser}@${VpsIp}:/tmp/fix_502.sh")
& scp @ScpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERRO: Falha no SCP (codigo $LASTEXITCODE)." -ForegroundColor Red
    Write-Host "Teste: ssh ${VpsUser}@${VpsIp} echo ok" -ForegroundColor Yellow
    exit 1
}
Write-Host "  Script enviado." -ForegroundColor Green

# --- PASSO 2: Executa na VPS ---
Write-Host ""
Write-Host "[2/2] Executando fix_502.sh na VPS (pode demorar 5-10 minutos)..." -ForegroundColor Yellow
Write-Host "      O rebuild dos containers Docker leva alguns minutos." -ForegroundColor DarkGray
Write-Host ""

$RemoteCmd = "chmod +x /tmp/fix_502.sh && sudo bash /tmp/fix_502.sh 2>&1"
$SshRunArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteCmd)
& ssh @SshRunArgs
$ExitCode = $LASTEXITCODE

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan

if ($ExitCode -eq 0) {
    Write-Host "  SUCESSO: 502 corrigido!" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Acesse: https://xavierbr-vps.tech/portalcursos.ng" -ForegroundColor Green
    Write-Host ""
    Write-Host "  NOTA sobre ERR_BLOCKED_BY_CLIENT:" -ForegroundColor DarkGray
    Write-Host "    Esse erro (mixpanel.com) e bloqueado pelo ad blocker do Chrome." -ForegroundColor DarkGray
    Write-Host "    Nao afeta o funcionamento do portal - pode ignorar." -ForegroundColor DarkGray
} else {
    Write-Host "  ERRO: Script retornou codigo $ExitCode." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Diagnostico manual via SSH:" -ForegroundColor Yellow
    Write-Host "    ssh ${VpsUser}@${VpsIp}"
    Write-Host "    docker ps -a --filter name=portalcursos"
    Write-Host "    docker logs portalcursos_frontend --tail 50"
    Write-Host "    docker logs portalcursos_backend  --tail 50"
    Write-Host "    curl -sf http://127.0.0.1:3010/portalcursos.ng"
    exit 1
}

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
