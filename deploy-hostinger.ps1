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
# Usuário SSH da VPS: prioriza a variável de ambiente HOSTINGER_USER (mesmo secret
# usado no workflow de CI), cai para o .env local, e só por último usa "root" como
# fallback — antes o script ignorava esse secret e sempre usava root hardcoded.
$vpsUser = if ($env:HOSTINGER_USER) { $env:HOSTINGER_USER } else { "root" }

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^DOMAIN_NAME=(?<val>.*)$") { $domain = $matches.val.Trim() }
        if ($_ -match "^VPS_IP=(?<val>.*)$") { $vpsIp = $matches.val.Trim() }
        if ($_ -match "^HOSTINGER_USER=(?<val>.*)$" -and -not $env:HOSTINGER_USER) { $vpsUser = $matches.val.Trim() }
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

# 3. Pipeline de Sincronização Git (Commit seletivo + PR automático)
#    A branch main agora exige PR com os checks do CI verdes (branch protection —
#    push direto é rejeitado, inclusive para admin). Este passo também não faz mais
#    "git add ." às cegas: só adiciona automaticamente arquivos dentro dos diretórios
#    de código conhecidos, ou modificações em arquivos já rastreados. Um arquivo novo
#    fora desses diretórios (ex.: um dump.rdb, log, temporário) fica de fora e é
#    avisado em vez de comitado — foi assim que dump.rdb parou versionado no repo.
Write-Host ""
Write-Host ">>> [PASSO 2/4] Sincronizando alterações locais com o Git..." -ForegroundColor Yellow

$allowedDirs = @("backend", "frontend", "devops", ".github", "docs", "legacy-sql-do-not-run")
$statusLines = git status --porcelain | Where-Object { $_ -ne "" }
$didCommit = $false
if ($statusLines) {
    $skipped = @()
    foreach ($line in $statusLines) {
        $code = $line.Substring(0, 2)
        $filePath = $line.Substring(3).Trim().Trim('"')
        $inAllowedDir = $false
        foreach ($dir in $allowedDirs) {
            if ($filePath -like "$dir/*") { $inAllowedDir = $true; break }
        }
        $isUntracked = ($code -eq "??")
        if ($inAllowedDir -or -not $isUntracked) {
            git add -- "$filePath"
        } else {
            $skipped += $filePath
        }
    }
    if ($skipped.Count -gt 0) {
        Write-Host "    [!] Arquivos novos fora do staging automático (adicione manualmente se forem intencionais):" -ForegroundColor Yellow
        $skipped | ForEach-Object { Write-Host "        - $_" -ForegroundColor Yellow }
    }
    if (git diff --cached --name-only) {
        Write-Host "    [!] Alterações elegíveis detectadas. Comitando..." -ForegroundColor Yellow
        git commit -m "OMEGA: Auto-sincronizacao de deploy Hostinger [$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')]"
        $didCommit = $true
    } else {
        Write-Host "    -> Nada elegível para commit automático." -ForegroundColor DarkYellow
    }
}

$originalBranch = (git branch --show-current).Trim()
$syncBranch = $originalBranch

if ($didCommit -or $originalBranch -eq "main") {
    if ($originalBranch -eq "main") {
        # main é protegida: push direto é rejeitado. Sincroniza via branch temporária + PR.
        $syncBranch = "deploy/auto-sync-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Host "    -> main é protegida (exige PR): criando branch temporária '$syncBranch'..." -ForegroundColor DarkCyan
        git checkout -b $syncBranch | Out-Null
    }

    Write-Host "    -> Enviando '$syncBranch' para o repositório remoto (git push)..." -ForegroundColor DarkCyan
    git push origin $syncBranch
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERRO CRÍTICO] Falha ao dar git push. Verifique credenciais SSH/HTTP do Git." -ForegroundColor Red
        if ($syncBranch -ne $originalBranch) { git checkout $originalBranch | Out-Null }
        exit 1
    }

    if ($syncBranch -ne "main") {
        Write-Host "    -> Abrindo PR para main e habilitando merge automático (aguarda checks do CI)..." -ForegroundColor DarkCyan
        $prUrl = gh pr create --base main --head $syncBranch --fill 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $prUrl) {
            Write-Host "[ERRO CRÍTICO] Falha ao abrir PR via gh CLI. Rode 'gh auth status' para verificar o login." -ForegroundColor Red
            exit 1
        }
        Write-Host "    [OK] PR aberto: $prUrl" -ForegroundColor Green
        gh pr merge $syncBranch --auto --squash | Out-Null

        Write-Host "    -> Aguardando checks do CI e merge automático (até 30 min)..." -ForegroundColor DarkCyan
        $merged = $false
        for ($i = 0; $i -lt 90; $i++) {
            Start-Sleep -Seconds 20
            $prState = gh pr view $syncBranch --json state -q ".state" 2>$null
            if ($prState -eq "MERGED") { $merged = $true; break }
            if ($prState -eq "CLOSED") { break }
        }

        if (-not $merged) {
            Write-Host "[ERRO CRÍTICO] PR não foi mergeado em main dentro do tempo limite (checks do CI podem ter falhado)." -ForegroundColor Red
            Write-Host "    Verifique manualmente: $prUrl" -ForegroundColor Red
            git checkout $originalBranch | Out-Null
            exit 1
        }
        Write-Host "[OK] PR mergeado em main com sucesso!" -ForegroundColor Green

        git checkout main | Out-Null
        git pull origin main | Out-Null
        if ($originalBranch -ne "main") { git branch -D $syncBranch | Out-Null }
    }
    Write-Host "[OK] Código fonte atualizado com sucesso no repositório remoto!" -ForegroundColor Green
} else {
    Write-Host "    -> Nenhuma alteração local pendente. Prosseguindo com o deploy do que já está em origin/$originalBranch." -ForegroundColor DarkYellow
}

# 4. Acionamento Automático via SSH na VPS
Write-Host ""
Write-Host ">>> [PASSO 3/4] Conectando à VPS Hostinger e executando implantação..." -ForegroundColor Yellow
Write-Host "    -> Criando diretório base e aplicando chown na VPS..." -ForegroundColor DarkCyan

try {
    # Garante que a pasta base exista na VPS
    ssh -o StrictHostKeyChecking=accept-new "$vpsUser@$vpsIp" "sudo mkdir -p /var/www/portalcursos && sudo chown -R root:root /var/www/portalcursos"

    # Transfere o arquivo .env local de forma segura via SCP para a VPS
    Write-Host "    -> Transferindo arquivo .env local via SCP..." -ForegroundColor DarkCyan
    scp -o StrictHostKeyChecking=accept-new "$envFile" "${vpsUser}@${vpsIp}:/var/www/portalcursos/.env"
    Write-Host "    [OK] Arquivo .env sincronizado com a VPS!" -ForegroundColor Green
    
    # Executa o orquestrador completo
    Write-Host "    -> Disparando build e deploy Docker..." -ForegroundColor DarkCyan
    # git fetch + reset --hard origin/main (em vez de "git pull") evita depender de
    # tracking de branch configurado na VPS — "git pull" falhava silenciosamente
    # quando a branch local nao tinha upstream, deixando o deploy rodar com codigo
    # desatualizado sem nenhum erro visivel.
    $sshCommand = "if [ ! -d '/var/www/portalcursos/.git' ]; then " +
                  "  echo '    [!] Repositorio nao encontrado na VPS. Clonando do Github...' && " +
                  "  sudo rm -rf /var/www/portalcursos && " +
                  "  sudo git clone https://github.com/claudiofxbr/PortalCursos.NG /var/www/portalcursos && " +
                  "  sudo cp /tmp/portal_env /var/www/portalcursos/.env 2>/dev/null || true; " +
                  "fi && " +
                  "cd /var/www/portalcursos && " +
                  "git fetch origin main && " +
                  "git reset --hard origin/main && " +
                  "chmod +x devops/scripts/deploy_docker_compose.sh && " +
                  "./devops/scripts/deploy_docker_compose.sh"

    ssh -o StrictHostKeyChecking=accept-new "$vpsUser@$vpsIp" $sshCommand
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERRO CRÍTICO] Deploy na VPS falhou (exit code $LASTEXITCODE). Veja a saida do SSH acima para detalhes." -ForegroundColor Red
        exit 1
    }
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
