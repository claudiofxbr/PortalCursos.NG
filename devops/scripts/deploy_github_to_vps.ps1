#Requires -Version 7.0
# =================================================================
# deploy_github_to_vps.ps1
# PortalCursos.NG — Deploy completo: Local → GitHub → VPS Hostinger
#
# Modo interativo (padrao, requer PowerShell 7+ / pwsh):
#   cd "C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG"
#   pwsh .\devops\scripts\deploy_github_to_vps.ps1
#
# Modo totalmente automatizado (sem nenhuma pausa/pergunta):
#   pwsh .\devops\scripts\deploy_github_to_vps.ps1 -Unattended -Method Actions
#   pwsh .\devops\scripts\deploy_github_to_vps.ps1 -Unattended -Method Ssh -HostingerIp 185.241.52.100
#
# ATENCAO: NAO execute com "powershell" (v5). Use "pwsh" (v7+).
#
# Variaveis de ambiente opcionais (defina antes de rodar):
#   $env:HOSTINGER_IP           = "185.241.52.XXX"
#   $env:HOSTINGER_SSH_KEY_PATH = "C:\Users\VeKTI-01\.ssh\id_rsa"
# =================================================================

param(
    # Roda sem nenhuma pergunta interativa (Read-Host). Exige -Method.
    [switch]$Unattended,

    # "Actions": push e espera o GitHub Actions concluir o deploy.
    # "Ssh": faz o deploy manual via SSH direto para a VPS.
    [ValidateSet("Actions", "Ssh")]
    [string]$Method,

    # IP da VPS (equivalente a $env:HOSTINGER_IP). Obrigatorio com -Method Ssh
    # em modo unattended, a menos que a env var ja esteja definida.
    [string]$HostingerIp,

    # Mensagem de commit. Se omitida, usa o padrao com timestamp.
    [string]$CommitMessage,

    # Em modo Actions unattended, quanto tempo (segundos) esperar o deploy
    # concluir antes de abrir o browser mesmo assim.
    [int]$WaitTimeoutSeconds = 600
)

$ErrorActionPreference = "Stop"

if ($Unattended -and -not $Method) {
    Write-Host "    ERRO  -Unattended exige -Method Actions ou -Method Ssh" -ForegroundColor Red
    exit 1
}

# ── Configuracao ──────────────────────────────────────────────────
$ProjectRoot  = "C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG"
$GitHubRepo   = "https://github.com/claudiofxbr/PortalCursos.NG"
$GitBranch    = "main"
$VpsUser      = "root"
$AppUrl       = "https://xavierbr-vps.tech/portalcursos.ng"
$ApiHealthUrl = "https://xavierbr-vps.tech/api/health"
$LocalScript  = Join-Path $PSScriptRoot "deploy_vps.sh"

# Arquivos sensiveis que jamais devem ir ao git
# Wildcards cobrem variantes como .env.local, backend/.env.production, frontend/.env etc.
$SensitivePatterns = @("*.env", "*.env.*", "*.key", "*.pem")

# ── Helpers ───────────────────────────────────────────────────────
function Write-Step { param($n, $msg) Write-Host ""; Write-Host "[$n] $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg)     Write-Host "    OK    $msg" -ForegroundColor Green }
function Write-Warn { param($msg)     Write-Host "    AVISO $msg" -ForegroundColor Yellow }
function Write-Info { param($msg)     Write-Host "    INFO  $msg" -ForegroundColor DarkGray }
function Write-Fail { param($msg)     Write-Host "    ERRO  $msg" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host "  PORTALCURSOS.NG — Deploy Local > GitHub > VPS Hostinger"        -ForegroundColor Magenta
Write-Host "  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"                       -ForegroundColor DarkGray
Write-Host "=================================================================" -ForegroundColor Magenta

# ── FASE 1: Pre-requisitos locais ─────────────────────────────────
Write-Step "1/6" "Verificando pre-requisitos locais"

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

# ── FASE 2: Seguranca — evitar commit de arquivos sensiveis ───────
Write-Step "2/6" "Verificando seguranca do commit"

$stagedFiles = git diff --cached --name-only 2>&1
$unstagedFiles = git diff --name-only 2>&1
$allChangedFiles = ($stagedFiles + $unstagedFiles) | Sort-Object -Unique

foreach ($pattern in $SensitivePatterns) {
    $hits = $allChangedFiles | Where-Object { $_ -like $pattern }
    if ($hits) {
        Write-Warn "ARQUIVO SENSIVEL detectado: $($hits -join ', ')"
        Write-Warn "Esses arquivos NAO serao commitados (protecao automatica)."
    }
}
Write-Ok "Verificacao de seguranca concluida"

# ── FASE 3: Commit e push para o GitHub ───────────────────────────
Write-Step "3/6" "Enviando codigo ao GitHub"

$gitStatus = git status --porcelain 2>&1
if ($gitStatus) {
    Write-Host "    Arquivos alterados:" -ForegroundColor Yellow
    $gitStatus | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }

    # Pathspecs de exclusao usados em AMBAS as chamadas de git add abaixo —
    # devem espelhar $SensitivePatterns para nao reintroduzir a divergencia
    # que permitia a um .env passar despercebido pelo filtro.
    $ExcludePathspecs = @(':!*.env', ':!*.env.*', ':!*.key', ':!*.pem')

    # Atualiza arquivos ja rastreados, exceto sensiveis
    git add --update -- . @ExcludePathspecs
    # Adiciona novos arquivos que nao sejam sensiveis
    git add -- . @ExcludePathspecs

    $stagedCount = (git diff --cached --name-only 2>&1 | Measure-Object).Count
    if ($stagedCount -eq 0) {
        Write-Ok "Nenhum arquivo para commitar apos filtro de seguranca"
    } else {
        if ($Unattended) {
            $commitMsg = $CommitMessage
        } else {
            $commitMsg = Read-Host "    Mensagem do commit (Enter para usar padrao)"
        }
        if (-not $commitMsg) {
            $commitMsg = "deploy: atualizacao portalcursos.ng $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
        }

        git commit -m "$commitMsg"
        if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git commit" }
        Write-Ok "Commit: $commitMsg"
    }
} else {
    Write-Ok "Nenhuma alteracao local — commit atual sera usado"
}

Write-Info "Enviando para GitHub ($GitBranch)..."
git push origin $GitBranch
if ($LASTEXITCODE -ne 0) { Write-Fail "Falha no git push. Verifique autenticacao com o GitHub." }

$lastCommit = git log --oneline -1
Write-Ok "Push realizado: $lastCommit"

# ── FASE 4: GitHub Actions vs deploy manual ───────────────────────
Write-Step "4/6" "Escolha de metodo de deploy"
Write-Info "GitHub Actions: $GitHubRepo/actions"
Write-Host ""

if ($Method) {
    $useActionsChoice = $Method
} else {
    $answer = Read-Host "    GitHub Actions esta configurado com os Secrets (VPS_HOST, VPS_USER, VPS_SSH_KEY)? (s/N)"
    $useActionsChoice = if ($answer -match '^[sS]$') { "Actions" } else { "Ssh" }
}

if ($useActionsChoice -eq "Actions") {
    Write-Ok "Deploy via GitHub Actions iniciado automaticamente pelo push."
    Write-Host ""
    Write-Host "    Acompanhe em: $GitHubRepo/actions" -ForegroundColor Yellow
    Write-Host "    Aguarde ~8-12 minutos e o app estara disponivel em:" -ForegroundColor DarkGray
    Write-Host "    $AppUrl" -ForegroundColor Green
    Write-Host ""

    $devePersistir = $Unattended
    if (-not $Unattended) {
        $aguardar = Read-Host "    Aguardar deploy e abrir o browser automaticamente? (s/N)"
        $devePersistir = $aguardar -match '^[sS]$'
    }

    if ($devePersistir) {
        Write-Info "Aguardando ate $WaitTimeoutSeconds segundos para o deploy via Actions concluir..."
        $intervalo = 30
        for ($i = 0; $i -lt $WaitTimeoutSeconds; $i += $intervalo) {
            Start-Sleep -Seconds $intervalo
            $restante = $WaitTimeoutSeconds - $i - $intervalo
            Write-Info "Aguardando... $restante segundos restantes"
            try {
                $check = Invoke-WebRequest -Uri $ApiHealthUrl -UseBasicParsing -TimeoutSec 10 -SkipCertificateCheck -ErrorAction SilentlyContinue
                if ($check.StatusCode -eq 200 -and $check.Content -match '"status":"UP"') {
                    Write-Ok "Backend UP! Abrindo browser..."
                    Start-Process $AppUrl
                    exit 0
                }
            } catch { }
        }
        Write-Warn "Tempo esgotado. Abrindo browser mesmo assim..."
        Start-Process $AppUrl
    }
    exit 0
}

# ── FASE 5: Deploy manual via SSH ─────────────────────────────────
Write-Step "5/6" "Deploy manual na VPS via SSH"

$VpsIp = if ($HostingerIp) { $HostingerIp } else { $env:HOSTINGER_IP }
if (-not $VpsIp -and -not $Unattended) {
    $VpsIp = Read-Host "    Digite o IP da VPS Hostinger (ex: 185.241.52.100)"
}
if (-not $VpsIp) { Write-Fail "IP da VPS nao informado. Use -HostingerIp ou defina `$env:HOSTINGER_IP." }
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

Write-Info "[1/2] Enviando deploy_vps.sh para a VPS..."
$ScpArgs = $SshOpts + @($LocalScript, "${VpsUser}@${VpsIp}:/tmp/deploy_vps.sh")
& scp @ScpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Falha no SCP. Verifique acesso SSH: ssh ${VpsUser}@${VpsIp}"
}
Write-Ok "Script enviado para /tmp/deploy_vps.sh"

Write-Info "[2/2] Executando deploy na VPS (pode demorar 8-15 minutos)..."
Write-Info "      O build Docker do frontend leva mais tempo."
Write-Host ""

$RemoteCmd = "chmod +x /tmp/deploy_vps.sh; bash /tmp/deploy_vps.sh 2>&1"
$SshRunArgs = $SshOpts + @("${VpsUser}@${VpsIp}", $RemoteCmd)
& ssh @SshRunArgs
$ExitCode = $LASTEXITCODE

if ($ExitCode -ne 0) {
    Write-Host ""
    Write-Warn "Deploy terminou com erro (codigo $ExitCode)."
    Write-Host "    Para diagnosticar:" -ForegroundColor DarkGray
    Write-Host "      ssh ${VpsUser}@${VpsIp} 'docker logs portalcursos_backend --tail 50'" -ForegroundColor DarkGray
    Write-Host "      ssh ${VpsUser}@${VpsIp} 'docker logs portalcursos_frontend --tail 50'" -ForegroundColor DarkGray
    exit $ExitCode
}

# ── FASE 6: Verificacao final + abrir browser ─────────────────────
Write-Step "6/6" "Verificacao final e abertura do app"
Write-Info "Aguardando 10s para estabilizar containers..."
Start-Sleep -Seconds 10

$apiOk = $false
$feOk  = $false

# Verifica backend
try {
    $resp = Invoke-WebRequest -Uri $ApiHealthUrl -UseBasicParsing -TimeoutSec 20 -SkipCertificateCheck
    if ($resp.StatusCode -eq 200 -and $resp.Content -match '"status":"UP"') {
        Write-Ok "API health: UP (HTTP $($resp.StatusCode))"
        $apiOk = $true
    } else {
        Write-Warn "API retornou HTTP $($resp.StatusCode)"
    }
} catch {
    Write-Warn "API nao respondeu ainda (DNS pode levar alguns minutos): $($_.Exception.Message)"
}

# Verifica frontend
try {
    $feResp = Invoke-WebRequest -Uri $AppUrl -UseBasicParsing -TimeoutSec 20 -SkipCertificateCheck
    if ($feResp.StatusCode -eq 200) {
        Write-Ok "Frontend: OK (HTTP $($feResp.StatusCode))"
        $feOk = $true
    } else {
        Write-Warn "Frontend retornou HTTP $($feResp.StatusCode)"
    }
} catch {
    Write-Warn "Frontend nao respondeu ainda: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Magenta
if ($apiOk -and $feOk) {
    Write-Host "  STATUS: DEPLOY BEM-SUCEDIDO" -ForegroundColor Green
} elseif ($apiOk -or $feOk) {
    Write-Host "  STATUS: DEPLOY PARCIAL — verifique os logs" -ForegroundColor Yellow
} else {
    Write-Host "  STATUS: AGUARDANDO INICIALIZACAO (normal nos primeiros minutos)" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  Frontend: $AppUrl" -ForegroundColor Green
Write-Host "  API:      $ApiHealthUrl" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host ""

# Abre o browser automaticamente
Write-Ok "Abrindo o PortalCursos.NG no browser..."
Start-Process $AppUrl
