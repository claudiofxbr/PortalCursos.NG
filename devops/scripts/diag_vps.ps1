# diag_vps.ps1 - Envia e executa diagnostico na VPS
# PortalCursos.NG | xavierbr-vps.tech
#
# Como usar:
#   cd C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG
#   .\devops\scripts\diag_vps.ps1

$ErrorActionPreference = "Stop"

$VpsUser     = "root"
$LocalScript = Join-Path $PSScriptRoot "diag_vps.sh"

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
Write-Host "  DIAGNOSTICO VPS - PortalCursos.NG" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  VPS: ${VpsUser}@${VpsIp}" -ForegroundColor Yellow
Write-Host ""

$SshOpts = @(
    "-o", "StrictHostKeyChecking=accept-new",
    "-o", "ConnectTimeout=30",
    "-o", "ServerAliveInterval=60"
)

if ($SshKeyPath -and (Test-Path $SshKeyPath)) {
    $SshOpts += @("-i", $SshKeyPath)
}

Write-Host "[1/2] Enviando diag_vps.sh..." -ForegroundColor Yellow
$ScpArgs = $SshOpts + @($LocalScript, "${VpsUser}@${VpsIp}:/tmp/diag_vps.sh")
& scp @ScpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: Falha no SCP." -ForegroundColor Red
    exit 1
}

Write-Host "[2/2] Executando diagnostico (30 segundos)..." -ForegroundColor Yellow
Write-Host ""

$RemoteCmd = "chmod +x /tmp/diag_vps.sh && sudo bash /tmp/diag_vps.sh 2>&1"
$SshRunArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteCmd)
& ssh @SshRunArgs

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  Cole a saida acima para analise do problema." -ForegroundColor Yellow
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
