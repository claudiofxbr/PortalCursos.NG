# diag_502.ps1 - Diagnostico preciso do 502 Bad Gateway
# PortalCursos.NG | xavierbr-vps.tech
#
# Como usar:
#   cd C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG
#   .\devops\scripts\diag_502.ps1

$ErrorActionPreference = "Stop"

$VpsUser     = "root"
$LocalScript = Join-Path $PSScriptRoot "diag_502.sh"

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
Write-Host "  DIAGNOSTICO 502 - PortalCursos.NG" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  VPS: ${VpsUser}@${VpsIp}" -ForegroundColor Yellow
Write-Host ""

if (-not (Test-Path $LocalScript)) {
    Write-Host "ERRO: diag_502.sh nao encontrado em: $LocalScript" -ForegroundColor Red
    exit 1
}

$SshOpts = @(
    "-o", "StrictHostKeyChecking=accept-new",
    "-o", "ConnectTimeout=15",
    "-o", "ServerAliveInterval=30",
    "-o", "BatchMode=no"
)

if ($SshKeyPath -and (Test-Path $SshKeyPath)) {
    $SshOpts += @("-i", $SshKeyPath)
    Write-Host "  Chave SSH: $SshKeyPath" -ForegroundColor DarkGray
}

Write-Host "[1/2] Enviando diag_502.sh para a VPS..." -ForegroundColor Yellow
$ScpArgs = $SshOpts + @($LocalScript, "${VpsUser}@${VpsIp}:/tmp/diag_502.sh")
& scp @ScpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: Falha no SCP. Verifique acesso SSH para ${VpsUser}@${VpsIp}" -ForegroundColor Red
    exit 1
}
Write-Host "  Script enviado." -ForegroundColor Green

Write-Host ""
Write-Host "[2/2] Executando diagnostico na VPS..." -ForegroundColor Yellow
Write-Host ""

$RemoteCmd = "chmod +x /tmp/diag_502.sh && sudo bash /tmp/diag_502.sh 2>&1"
$SshRunArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteCmd)
& ssh @SshRunArgs
