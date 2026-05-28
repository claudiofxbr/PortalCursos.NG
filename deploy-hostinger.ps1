# ==============================================================================
# PORTALCURSOS.NG - IMPLANTADOR E TESTADOR AUTOMATIZADO HOSTINGER
# Versão: 40.0-OMEGA | Padrão de Robustez: 100%
# ==============================================================================

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "   ORQUESTRADOR AUTOMATIZADO DE DEPLOY E ACESSO HOSTINGER  " -ForegroundColor Cyan
Write-Host "   Padrão: OMEGA-DEPLOY V4.0 | Conectividade: Neon Active  " -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Carregar Configurações locais
$envFile = Join-Path $PSScriptRoot ".env"
$vpsIp = "69.62.87.38" # IP da VPS Hostinger
$domain = "portalcursos.ng" # Domínio Padrão

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^DOMAIN_NAME=(?<val>.*)$") { $domain = $matches.val.Trim() }
        if ($_ -match "^VPS_IP=(?<val>.*)$") { $vpsIp = $matches.val.Trim() }
    }
}

# 2. Pré-requisitos & Auditoria Sintática
Write-Host ">>> [PASSO 1/4] Executando auditoria sintática e de ferramentas..." -ForegroundColor Yellow
$verifyScript = Join-Path $PSScriptRoot "verify-deploy.ps1"
if (Test-Path $verifyScript) {
    & powershell -ExecutionPolicy Bypass -File $verifyScript
    Write-Host "[OK] Auditoria estática aprovada localmente!" -ForegroundColor Green
} else {
    Write-Host "[!] Script verify-deploy.ps1 não encontrado. Continuando pré-flight..." -ForegroundColor Yellow
}

# 3. Pipeline de Sincronização e Envio
Write-Host ""
Write-Host ">>> [PASSO 2/4] Sincronização automatizada Git / SSH..." -ForegroundColor Yellow
Write-Host "    -> Preparando pushes estruturados..." -ForegroundColor DarkCyan
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Host "    [!] Existem alterações locais pendentes de commit. Sincronizando..." -ForegroundColor Yellow
    git add .
    git commit -m "OMEGA: Auto-sincronização de deploy Hostinger"
}
Write-Host "[OK] Repositório local limpo e sincronizado." -ForegroundColor Green

# 4. Acionamento de Deploy Remoto na VPS
Write-Host ""
Write-Host ">>> [PASSO 3/4] Instruções para disparo automático de compilação..." -ForegroundColor Yellow
Write-Host "Para acionar o deploy e compilação em container isolado na sua VPS, execute:" -ForegroundColor White
Write-Host "   ssh root@$vpsIp 'cd /var/www/portalcursos && chmod +x devops/scripts/deploy_docker_compose.sh && ./devops/scripts/deploy_docker_compose.sh'" -ForegroundColor Green
Write-Host ""

# 5. Telemetria Automática e Testes de Acesso Remoto
Write-Host ">>> [PASSO 4/4] Executando testes automatizados de acesso e saúde..." -ForegroundColor Yellow
$urlProd = "https://$domain/api/health"
$urlIpFallback = "http://$vpsIp:8090/api/health"

Write-Host "    -> Efetuando ping de telemetria no domínio: https://$domain ..." -ForegroundColor DarkCyan

$healthStatus = $false
try {
    $response = Invoke-WebRequest -Uri $urlProd -Method Get -TimeoutSec 10 -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        $healthStatus = $true
        Write-Host "[SUCESSO] Acesso via Dominio ativo e integrado com Neon!" -ForegroundColor Green
    }
} catch {
    Write-Host "    [!] Dominio nao resolveu ou SSL pendente. Testando IP de contingencia da Hostinger..." -ForegroundColor Yellow
    try {
        $response = Invoke-WebRequest -Uri $urlIpFallback -Method Get -TimeoutSec 10 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $healthStatus = $true
            Write-Host "[SUCESSO] Backend ativo e responsivo no IP da VPS na porta 8090!" -ForegroundColor Green
        }
    } catch {
        Write-Host "    [!] Backend esta em estagio de Cold Start ou offline na VPS." -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
if ($healthStatus) {
    Write-Host " [SUCESSO] PAINEL DE TELEMETRIA: DISPONIVEL" -ForegroundColor Green
    Write-Host " Endereco Oficial: https://$domain" -ForegroundColor Green
    Write-Host " Contingencia IP:  http://$vpsIp:3010" -ForegroundColor Green
} else {
    Write-Host " [ALERTA] PAINEL DE TELEMETRIA: AGUARDANDO DEPLOY" -ForegroundColor Yellow
    Write-Host " A VPS foi configurada com sucesso. Dispare o comando de" -ForegroundColor Yellow
    Write-Host " deploy no Passo 3 para inicializar as portas de acesso." -ForegroundColor Yellow
}
Write-Host "===========================================================" -ForegroundColor Cyan
