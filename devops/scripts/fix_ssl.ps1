# fix_ssl.ps1 - Corrige SSL (ERR_CERT_AUTHORITY_INVALID) na VPS Hostinger
# PortalCursos.NG | xavierbr-vps.tech
#
# Como usar:
#   cd C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG
#   .\devops\scripts\fix_ssl.ps1

$ErrorActionPreference = "Stop"

# --- Configuracoes ---
$VpsUser    = "root"
$LocalScript = Join-Path $PSScriptRoot "fix_ssl.sh"

# --- Pede o IP se nao foi passado como variavel de ambiente ---
$VpsIp = $env:HOSTINGER_IP
if (-not $VpsIp) {
    $VpsIp = Read-Host "Digite o IP da VPS Hostinger (ex: 185.xxx.xxx.xxx)"
}
if (-not $VpsIp) {
    Write-Host "ERRO: IP da VPS nao informado. Abortando." -ForegroundColor Red
    exit 1
}

# --- Chave SSH opcional ---
$SshKeyPath = $env:HOSTINGER_SSH_KEY_PATH

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  FIX SSL - PortalCursos.NG | xavierbr-vps.tech" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  VPS: ${VpsUser}@${VpsIp}" -ForegroundColor Yellow
Write-Host "  Script local: $LocalScript" -ForegroundColor DarkGray
Write-Host ""

# --- Verifica se o script local existe ---
if (-not (Test-Path $LocalScript)) {
    Write-Host "ERRO: fix_ssl.sh nao encontrado em: $LocalScript" -ForegroundColor Red
    Write-Host "Certifique-se de estar na raiz do projeto." -ForegroundColor Yellow
    exit 1
}

# --- Monta argumentos SSH/SCP comuns ---
$SshOpts = @(
    "-o", "StrictHostKeyChecking=accept-new",
    "-o", "ConnectTimeout=30",
    "-o", "ServerAliveInterval=60",
    "-o", "BatchMode=no"
)

if ($SshKeyPath -and (Test-Path $SshKeyPath)) {
    $SshOpts += @("-i", $SshKeyPath)
    Write-Host "  Chave SSH: $SshKeyPath" -ForegroundColor DarkGray
}

# --- PASSO 1: Envia o script para a VPS via SCP ---
Write-Host ""
Write-Host "[1/2] Enviando fix_ssl.sh para a VPS..." -ForegroundColor Yellow

$ScpArgs = $SshOpts + @($LocalScript, "${VpsUser}@${VpsIp}:/tmp/fix_ssl.sh")

& scp @ScpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERRO: Falha ao enviar o script via SCP (codigo $LASTEXITCODE)." -ForegroundColor Red
    Write-Host ""
    Write-Host "Verifique:" -ForegroundColor Yellow
    Write-Host "  1. IP correto? $VpsIp"
    Write-Host "  2. Acesso SSH configurado (chave ou senha)?"
    Write-Host "  3. Teste: ssh ${VpsUser}@${VpsIp} echo ok"
    exit 1
}

Write-Host "  Script enviado com sucesso." -ForegroundColor Green

# --- PASSO 2: Executa na VPS via SSH ---
Write-Host ""
Write-Host "[2/2] Executando fix_ssl.sh na VPS (2-5 minutos)..." -ForegroundColor Yellow
Write-Host "      Saida em tempo real:" -ForegroundColor DarkGray
Write-Host ""

$RemoteCmd = "chmod +x /tmp/fix_ssl.sh && sudo bash /tmp/fix_ssl.sh 2>&1"
$SshRunArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteCmd)

& ssh @SshRunArgs
$ExitCode = $LASTEXITCODE

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan

if ($ExitCode -eq 0) {
    Write-Host "  SUCESSO: SSL corrigido!" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Acesse: https://xavierbr-vps.tech/portalcursos.ng" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Se o Chrome ainda mostrar erro:" -ForegroundColor Yellow
    Write-Host "    1. Ctrl+Shift+Delete -> limpar cache e cookies"
    Write-Host "    2. Aba anonima: Ctrl+Shift+N"
    Write-Host "    3. Validacao: https://www.ssllabs.com/ssltest/analyze.html?d=xavierbr-vps.tech"
} else {
    Write-Host "  ERRO: O script retornou codigo $ExitCode." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Diagnostico manual (conecte via SSH e execute):" -ForegroundColor Yellow
    Write-Host "    ssh ${VpsUser}@${VpsIp}"
    Write-Host "    sudo certbot certificates"
    Write-Host "    sudo nginx -t"
    Write-Host "    sudo systemctl status nginx"
    Write-Host "    sudo journalctl -u nginx -n 30"
    Write-Host "    curl -I http://xavierbr-vps.tech/.well-known/acme-challenge/test"
    exit 1
}

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
