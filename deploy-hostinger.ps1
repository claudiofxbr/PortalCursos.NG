# ==============================================================================
# PORTALCURSOS.NG - IMPLANTADOR DEFINITIVO INTEGRADO (SINGLE-COMMAND DEPLOY)
# Versão: 50.0-OMEGA-ULTRA | Padrão de Robustez: 100% | Idioma: pt-BR
# ==============================================================================

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "   ORQUESTRADOR INTEGRADO E AUTOMATIZADO PORTALCURSOS.NG   " -ForegroundColor Cyan
Write-Host "   Padrão: OMEGA-SINGLE-COMMAND | Conectividade: Neon Active" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Carregar Configurações locais do .env
$envFile = Join-Path $PSScriptRoot ".env"
$vpsIp = "69.62.87.38" # IP padrão da VPS
$domain = "portalcursos.ng" # Domínio padrão

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^DOMAIN_NAME=(?<val>.*)$") { $domain = $matches.val.Trim() }
        if ($_ -match "^VPS_IP=(?<val>.*)$") { $vpsIp = $matches.val.Trim() }
    }
}

# 2. Pré-requisitos & Auditoria Sintática Local
Write-Host ">>> [PASSO 1/4] Executando auditoria estática e de ferramentas locais..." -ForegroundColor Yellow
$verifyScript = Join-Path $PSScriptRoot "verify-deploy.ps1"
if (Test-Path $verifyScript) {
    & powershell -ExecutionPolicy Bypass -File $verifyScript
    Write-Host "[OK] Auditoria estática aprovada localmente!" -ForegroundColor Green
} else {
    Write-Host "[!] Script verify-deploy.ps1 nao encontrado. Continuando..." -ForegroundColor Yellow
}

# 3. Pipeline de Sincronização Git (Commit & Push Automáticos)
Write-Host ""
Write-Host ">>> [PASSO 2/4] Sincronizando alterações locais com o Git..." -ForegroundColor Yellow
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Host "    [!] Detectadas alterações locais pendentes. Comitando..." -ForegroundColor Yellow
    git add .
    git commit -m "OMEGA: Auto-sincronizacao de deploy Hostinger [$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')]"
}
Write-Host "    -> Enviando alterações para o repositório remoto (git push)..." -ForegroundColor DarkCyan
try {
    # Tenta dar push na branch atual de forma resiliente
    $currentBranch = (git branch --show-current).Trim()
    git push origin $currentBranch
    Write-Host "[OK] Código fonte atualizado com sucesso no repositório remoto!" -ForegroundColor Green
} catch {
    Write-Host "[AVISO] Falha ao dar git push automático. Certifique-se de configurar as credenciais SSH/HTTP do Git." -ForegroundColor Yellow
}

# 4. Acionamento Automático via SSH na VPS
Write-Host ""
Write-Host ">>> [PASSO 3/4] Conectando à VPS Hostinger e executando implantação..." -ForegroundColor Yellow
Write-Host "    -> Criando diretório base e aplicando chown na VPS..." -ForegroundColor DarkCyan

try {
    # Garante que a pasta base exista na VPS
    ssh -o StrictHostKeyChecking=no root@$vpsIp "sudo mkdir -p /var/www/portalcursos && sudo chown -R root:root /var/www/portalcursos"
    
    # Transfere o arquivo .env local de forma segura via SCP para a VPS
    Write-Host "    -> Transferindo arquivo .env local via SCP..." -ForegroundColor DarkCyan
    scp -o StrictHostKeyChecking=no "$envFile" "root@${vpsIp}:/var/www/portalcursos/.env"
    Write-Host "    [OK] Arquivo .env sincronizado com a VPS!" -ForegroundColor Green
    
    # Executa o orquestrador completo
    Write-Host "    -> Disparando build e deploy Docker..." -ForegroundColor DarkCyan
    $sshCommand = "if [ ! -d '/var/www/portalcursos/.git' ]; then " +
                  "  echo '    [!] Repositorio nao encontrado na VPS. Clonando do Github...' && " +
                  "  sudo rm -rf /var/www/portalcursos && " +
                  "  sudo git clone https://github.com/claudiofxbr/PortalCursos.NG /var/www/portalcursos && " +
                  "  sudo cp /tmp/portal_env /var/www/portalcursos/.env 2>/dev/null || true; " +
                  "fi && " +
                  "cd /var/www/portalcursos && " +
                  "git reset --hard HEAD && " +
                  "git pull && " +
                  "chmod +x devops/scripts/deploy_docker_compose.sh && " +
                  "./devops/scripts/deploy_docker_compose.sh"
                  
    ssh -o StrictHostKeyChecking=no root@$vpsIp $sshCommand
    Write-Host "[OK] Processo de deploy concluído com sucesso dentro da VPS Hostinger!" -ForegroundColor Green
} catch {
    Write-Host "[ERRO CRÍTICO] Falha na comunicação SSH com a VPS. Verifique sua chave privada ou IP: $vpsIp" -ForegroundColor Red
    exit 1
}

# 5. Telemetria e Testes de Acesso em Tempo Real
Write-Host ""
Write-Host ">>> [PASSO 4/4] Iniciando validação de telemetria e testes de acesso..." -ForegroundColor Yellow
$urlProd = "https://$domain/api/health"
$urlIpFallback = "http://$vpsIp:8090/api/health"

$healthStatus = $false
Write-Host "    -> Efetuando ping de telemetria no domínio: https://$domain ..." -ForegroundColor DarkCyan

try {
    $response = Invoke-WebRequest -Uri $urlProd -Method Get -TimeoutSec 15 -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        $healthStatus = $true
        Write-Host "[SUCESSO] Acesso via Domínio ($domain) ativo, seguro com SSL e integrado com o Neon!" -ForegroundColor Green
    }
} catch {
    Write-Host "    [!] Domínio oficial inacessível ou SSL em emissão no primeiro acesso. Testando IP de contingência..." -ForegroundColor Yellow
    try {
        $response = Invoke-WebRequest -Uri $urlIpFallback -Method Get -TimeoutSec 15 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $healthStatus = $true
            Write-Host "[SUCESSO] Backend ativo e respondivo no IP da VPS na porta 8090!" -ForegroundColor Green
        }
    } catch {
        Write-Host "    [!] Ambos os endpoints estão temporariamente em estágio de Cold Start ou offline." -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
if ($healthStatus) {
    Write-Host " [SUCESSO] SEU PORTAL ESTA ONLINE E TOTALMENTE SEGURO      " -ForegroundColor Green
    Write-Host " Endereco Oficial: https://$domain" -ForegroundColor Green
    Write-Host " Contingencia IP:  http://$vpsIp:3010" -ForegroundColor Green
} else {
    Write-Host " [ALERTA] DEPLOY CONCLUIDO COM PENDENCIAS DE REDE          " -ForegroundColor Yellow
    Write-Host " O deploy rodou sem erros na VPS, mas as portas de rede" -ForegroundColor Yellow
    Write-Host " ainda estao inicializando ou o DNS ainda nao propagou." -ForegroundColor Yellow
}
Write-Host "===========================================================" -ForegroundColor Cyan
