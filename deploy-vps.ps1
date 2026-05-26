<#
.SYNOPSIS
    OMEGA-SUPREME: Painel Interativo e Orquestrador de Deploy VPS V3.7
    PortalCursos.NG - Next.js 16 (Frontend) + Spring Boot (Backend) + PostgreSQL (DB)
.DESCRIPTION
    Este script automatiza, otimiza e valida todo o fluxo de deploy local a partir
    do Windows para a VPS da Hostinger Linux (Ubuntu), utilizando Docker Compose e SSL.
.PARAMETER VpsIp
    O endereco IP publico da VPS de destino.
.PARAMETER VpsUser
    O usuario SSH da VPS de destino (padrao: root).
.PARAMETER VpsPath
    Caminho absoluto na VPS para clonar o repositorio (padrao: /var/www/portalcursos).
.PARAMETER PrivateKeyPath
    Caminho local para uma chave privada SSH especifica a ser usada nas conexoes.
.PARAMETER SkipGitPush
    Se definido, o script pulara o push local para o GitHub antes de atualizar a VPS.
.PARAMETER NonInteractive
    Se definido, executa o deploy silenciosamente sem menus interativos (ideal para CI/CD).
#>
[CmdletBinding()]
Param(
    [Parameter(Mandatory = $false)]
    [string]$VpsIp = "",

    [Parameter(Mandatory = $false)]
    [string]$VpsUser = "root",

    [string]$VpsPath = "/var/www/portalcursos",

    [string]$PrivateKeyPath = "",

    [switch]$SkipGitPush = $false,

    [switch]$NonInteractive = $false
)

# Usamos Continue em scripts que executam CLI nativos para evitar que mensagens de stderr quebrem a execucao
$ErrorActionPreference = "Continue"
$OutputEncoding = [System.Text.Encoding]::UTF8

# Configuracao de Logs Locais
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = Get-Location }
$global:LogFile = Join-Path $ScriptDir "deploy-vps.log"
$global:QuietMode = $NonInteractive

# Inicializa variaveis globais obtidas por parametro
$global:VpsIp = $VpsIp
$global:VpsUser = $VpsUser
$global:vpsPath = $VpsPath
$global:PrivateKeyPath = $PrivateKeyPath

# --- FUNCAO DE LOGGING E SAIDA DE TEXTO ---
function Write-Log {
    Param(
        [Parameter(Mandatory = $false)]
        [AllowEmptyString()]
        [string]$Message = "",
        [string]$Level = "INFO",
        [string]$Color = "White"
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logLine = "[" + $timestamp + "] [" + $Level + "] " + $Message
    Add-Content -Path $global:LogFile -Value $logLine -Encoding UTF8 -ErrorAction SilentlyContinue
    
    if (-not $global:QuietMode) {
        Write-Host $Message -ForegroundColor $Color
    }
}

# --- FUNCAO DE CABECALHO SUPREMO ---
function Show-DeployHeader {
    if ($global:QuietMode) { return }
    Clear-Host
    Write-Host "===========================================================" -ForegroundColor Cyan
    Write-Host "   PORTALCURSOS.NG - PAINEL INTERATIVO DE DEPLOY VPS        " -ForegroundColor Cyan
    Write-Host "   Versao: OMEGA-SUPREME V3.7 | Resiliencia: Ultra-Ativa   " -ForegroundColor Cyan
    Write-Host "===========================================================" -ForegroundColor Cyan
    if ($global:VpsIp) {
        Write-Host ("   VPS Alvo: " + $global:VpsUser + "@" + $global:VpsIp) -ForegroundColor Green
        if ($global:PrivateKeyPath) {
            # Se for chave valida
            if (Test-Path $global:PrivateKeyPath -ErrorAction SilentlyContinue) {
                Write-Host ("   Chave SSH: " + $global:PrivateKeyPath) -ForegroundColor Green
            } else {
                Write-Host "   Conexao: Autenticacao por Senha (Chave nao carregada)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "   Conexao: Autenticacao por Senha ou Chave Padrao SSH" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   VPS Alvo: NAO CONFIGURADA (Selecione a opcao 1)" -ForegroundColor Yellow
    }
    Write-Host "===========================================================" -ForegroundColor Cyan
}

# --- FUNCAO AUXILIAR DE CONEXAO SSH COM PARAMETROS ---
function Invoke-SshCommand {
    Param(
        [Parameter(Mandatory = $true)]
        [string]$Command
    )
    if (-not $global:VpsIp) {
        throw "O endereco IP da VPS nao esta configurado."
    }

    $sshTarget = $global:VpsUser + "@" + $global:VpsIp
    $sshArgs = @()
    
    # Adiciona chave privada apenas se for um arquivo real existente no Windows
    if ($global:PrivateKeyPath) {
        if (Test-Path $global:PrivateKeyPath -ErrorAction SilentlyContinue) {
            $sshArgs += "-i", $global:PrivateKeyPath
        } else {
            Write-Log "[INFO] Ignorando parametro -i (o texto digitado nao e um arquivo de chave privada valido)." "INFO" "Yellow"
        }
    }
    
    # Parametros recomendados para robustez de conexao
    $sshArgs += "-o", "ConnectTimeout=10"
    $sshArgs += "-o", "StrictHostKeyChecking=no"
    $sshTargetClean = $sshTarget.Trim()
    $sshArgs += $sshTargetClean
    $sshArgs += $Command

    # Executa o SSH local capturando o codigo de saida
    $output = & ssh $sshArgs 2>&1
    $exitCode = $LASTEXITCODE

    return [PSCustomObject]@{
        Output = $output
        ExitCode = $exitCode
    }
}

# --- FUNCAO AUXILIAR DE TRANSFERENCIA SCP ---
function Invoke-ScpTransfer {
    Param(
        [Parameter(Mandatory = $true)]
        [string]$LocalFilePath,
        [Parameter(Mandatory = $true)]
        [string]$RemoteDestinationPath
    )
    if (-not $global:VpsIp) {
        throw "O endereco IP da VPS nao esta configurado."
    }

    $scpTarget = $global:VpsUser + "@" + $global:VpsIp + ":" + $RemoteDestinationPath
    $scpArgs = @()
    
    # Adiciona chave privada apenas se for um arquivo real existente no Windows
    if ($global:PrivateKeyPath) {
        if (Test-Path $global:PrivateKeyPath -ErrorAction SilentlyContinue) {
            $scpArgs += "-i", $global:PrivateKeyPath
        }
    }
    
    $scpArgs += "-o", "ConnectTimeout=10"
    $scpArgs += "-o", "StrictHostKeyChecking=no"
    $scpArgs += $LocalFilePath
    $scpTargetClean = $scpTarget.Trim()
    $scpArgs += $scpTargetClean

    # Executa a copia SCP
    & scp $scpArgs 2>&1 | Out-Null
    return $LASTEXITCODE
}

# --- FUNCAO DE AUTO-GERACAO E INSTALACAO DE CHAVE SSH ---
function Install-LocalSshKeyToVps {
    if ($global:QuietMode) { return }
    Show-DeployHeader
    Write-Log "[SSH-MANAGER] Iniciando Assistente de Chave SSH..." "INFO" "Yellow"

    $sshKeyDir = Join-Path $HOME ".ssh"
    $privateKey = Join-Path $sshKeyDir "id_rsa"
    $publicKey = Join-Path $sshKeyDir "id_rsa.pub"

    if (-not (Test-Path $publicKey)) {
        Write-Log "[ALERTA] Chave SSH local nao localizada no diretorio padrao." "WARN" "Yellow"
        $choice = Read-Host "Deseja gerar uma nova chave SSH RSA de 4096 bits agora? (S/N)"
        if ($choice.ToUpper() -eq "S") {
            if (-not (Test-Path $sshKeyDir)) {
                New-Item -ItemType Directory -Path $sshKeyDir | Out-Null
            }
            Write-Log "Gerando chave SSH local..." "INFO" "Cyan"
            & ssh-keygen -t rsa -b 4096 -N '""' -f $privateKey
            Write-Log "[SUCESSO] Chave SSH local criada com sucesso!" "INFO" "Green"
        } else {
            Write-Log "[CANCELADO] Geracao de chave cancelada pelo usuario." "INFO" "Red"
            Start-Sleep -Seconds 2
            return
        }
    } else {
        Write-Log "[OK] Chave SSH local detectada com sucesso." "INFO" "Green"
    }

    if (-not $global:VpsIp) {
        Write-Log "[ERRO] Configure a VPS Alvo antes de autorizar a chave SSH local." "INFO" "Red"
        Start-Sleep -Seconds 2
        return
    }

    Write-Log ("Conectando para copiar a chave publica para " + $global:VpsIp + "...") "INFO" "Yellow"
    $pubContent = Get-Content $publicKey -Raw
    $pubContentClean = $pubContent.Trim()

    # Comando Linux seguro para autorizar a chave SSH
    $linuxAuthCmd = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo '" + $pubContentClean + "' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo 'SSH_KEY_INJECT_SUCCESS'"

    Write-Host "Insira a senha SSH da VPS se solicitado abaixo:" -ForegroundColor Yellow
    $res = Invoke-SshCommand -Command $linuxAuthCmd
    
    if ($res.Output -match "SSH_KEY_INJECT_SUCCESS" -and $res.ExitCode -eq 0) {
        Write-Log "[SUCESSO] A chave SSH publica foi instalada e autorizada na VPS com sucesso!" "INFO" "Green"
        Write-Log "  A partir de agora, o deploy nao solicitara mais senha!" "INFO" "Green"
    } else {
        Write-Log ("[ERRO] Falha ao injetar chave SSH. Detalhes: " + $res.Output) "ERROR" "Red"
    }
    
    Write-Host ""
    Read-Host "Pressione Enter para voltar ao menu"
}

# --- FUNCAO DE AUTO-PROVISIONAMENTO E DIAGNOSTICO DA VPS ---
function Test-VpsPrerequisites {
    Write-Log "Iniciando Diagnostico Pre-Voo e Infraestrutura da VPS..." "INFO" "Yellow"
    
    $diagCmd = "echo '=== DIAGNOSTIC ===' && " +
               "if command -v docker >/dev/null 2>&1; then echo 'DOCKER: OK'; else echo 'DOCKER: MISSING'; fi && " +
               "if command -v git >/dev/null 2>&1; then echo 'GIT: OK'; else echo 'GIT: MISSING'; fi && " +
               "if command -v docker-compose >/dev/null 2>&1 || docker compose version >/dev/null 2>&1; then echo 'COMPOSE: OK'; else echo 'COMPOSE: MISSING'; fi && " +
               "if command -v nginx >/dev/null 2>&1; then echo 'NGINX: OK'; else echo 'NGINX: MISSING'; fi && " +
               "if command -v ufw >/dev/null 2>&1; then echo 'UFW: OK'; else echo 'UFW: MISSING'; fi"

    $res = Invoke-SshCommand -Command $diagCmd
    if ($res.ExitCode -ne 0) {
        Write-Log ("[ERRO] Erro critico ao se conectar via SSH na VPS para diagnostico: " + $res.Output) "ERROR" "Red"
        return $false
    }

    Write-Log "Relatorio Tecnico de Recursos Remotos na VPS:" "INFO" "Cyan"
    $outputString = $res.Output | Out-String
    
    $dockerOk = $outputString -match "DOCKER: OK"
    $gitOk = $outputString -match "GIT: OK"
    $composeOk = $outputString -match "COMPOSE: OK"
    $nginxOk = $outputString -match "NGINX: OK"
    $ufwOk = $outputString -match "UFW: OK"

    $statusDocker = if ($dockerOk) { "INSTALADO [OK]" } else { "AUSENTE [X]" }
    $colorDocker = if ($dockerOk) { "Green" } else { "Red" }
    Write-Log ("  Docker Engine:    " + $statusDocker) "INFO" $colorDocker

    $statusGit = if ($gitOk) { "INSTALADO [OK]" } else { "AUSENTE [X]" }
    $colorGit = if ($gitOk) { "Green" } else { "Red" }
    Write-Log ("  Git Remoto:        " + $statusGit) "INFO" $colorGit

    $statusCompose = if ($composeOk) { "INSTALADO [OK]" } else { "AUSENTE [X]" }
    $colorCompose = if ($composeOk) { "Green" } else { "Red" }
    Write-Log ("  Docker Compose:   " + $statusCompose) "INFO" $colorCompose

    $statusNginx = if ($nginxOk) { "INSTALADO [OK]" } else { "AUSENTE [X]" }
    $colorNginx = if ($nginxOk) { "Green" } else { "Red" }
    Write-Log ("  Nginx Server:      " + $statusNginx) "INFO" $colorNginx

    $statusUfw = if ($ufwOk) { "INSTALADO [OK]" } else { "AUSENTE [X]" }
    $colorUfw = if ($ufwOk) { "Green" } else { "Red" }
    Write-Log ("  Firewall UFW:     " + $statusUfw) "INFO" $colorUfw

    # AUTO-PROVISIONAMENTO SE AUSENTE
    if (-not $dockerOk -or -not $composeOk -or -not $gitOk) {
        Write-Log "[ALERTA] Algumas dependencias criticas de Docker/Git estao ausentes na VPS!" "WARN" "Yellow"
        
        $installChoice = "N"
        if (-not $global:QuietMode) {
            $installChoice = Read-Host "Deseja que o script provisione e instale DOCKER e GIT automaticamente na VPS? (S/N)"
        } else {
            $installChoice = "S" # Auto-aceitacao em modo CI/CD
        }

        if ($installChoice.ToUpper() -eq "S") {
            Write-Log "Iniciando auto-provisionamento de Docker e Git na VPS (pode levar alguns minutos)..." "INFO" "Cyan"
            
            $setupCmd = "sudo apt-get update && sudo apt-get install -y git curl apt-transport-https ca-certificates gnupg lsb-release && " +
                        "sudo mkdir -p /etc/apt/keyrings && " +
                        "curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -y --batch --yes -o /etc/apt/keyrings/docker.gpg 2>/dev/null || true && " +
                        "echo 'deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable' | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null && " +
                        "sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin && " +
                        "sudo systemctl enable docker && sudo systemctl start docker && echo 'PROVISIONING_COMPLETE_SUCCESS'"

            Write-Progress -Activity "Provisionando VPS" -Status "Instalando Docker Engine e Dependencias..." -PercentComplete 30
            $setupRes = Invoke-SshCommand -Command $setupCmd
            Write-Progress -Activity "Provisionando VPS" -Completed

            if ($setupRes.Output -match "PROVISIONING_COMPLETE_SUCCESS" -and $setupRes.ExitCode -eq 0) {
                Write-Log "[SUCESSO] Docker, Compose e Git foram instalados e provisionados na VPS com sucesso!" "INFO" "Green"
            } else {
                Write-Log ("[ERRO] Erro no provisionamento remoto: " + $setupRes.Output) "ERROR" "Red"
                return $false
            }
        } else {
            Write-Log "[ERRO] Abortando deploy devido a dependencias ausentes na VPS." "ERROR" "Red"
            return $false
        }
    }

    Write-Log "[SUCESSO] VPS Alvo validada e 100% pronta para receber o Deploy!" "INFO" "Green"
    return $true
}

# --- FUNCAO DE COMPILACAO E DISPARO DE DEPLOY COMPLETO ---
function Invoke-SupremeDeploy {
    if (-not $global:VpsIp) {
        Write-Log "[ERRO] Configure o IP da VPS Alvo antes de iniciar o deploy." "ERROR" "Red"
        if (-not $global:QuietMode) {
            Start-Sleep -Seconds 2
        } else {
            exit 1
        }
        return
    }

    try {
        Show-DeployHeader
        Write-Log "Iniciando Protocolo de Deploy Supremo V3.7..." "INFO" "Green"
        Write-Progress -Activity "Deploy Supremo" -Status "Fazendo diagnosticos locais..." -PercentComplete 10

        # 1. Validacao de Dependencias locais
        $localGit = Get-Command git -ErrorAction SilentlyContinue
        $localSsh = Get-Command ssh -ErrorAction SilentlyContinue
        $localScp = Get-Command scp -ErrorAction SilentlyContinue
        $localEnv = Join-Path $ScriptDir ".env"

        if (-not $localGit -or -not $localSsh -or -not $localScp) {
            throw "Dependencias locais ausentes (Git, SSH ou SCP). Certifique-se de que estao ativas no Windows."
        }
        if (-not (Test-Path $localEnv)) {
            throw "Arquivo de configuracao .env local nao localizado na raiz."
        }

        # 2. Diagnostico da VPS
        Write-Progress -Activity "Deploy Supremo" -Status "Iniciando Diagnostico Remoto VPS..." -PercentComplete 25
        $vpsReady = Test-VpsPrerequisites
        if (-not $vpsReady) {
            throw "A VPS nao atende aos pre-requisitos logicos para deploy."
        }

        # 3. Sincronizacao GitHub
        if (-not $SkipGitPush) {
            Write-Log "" "INFO" "White"
            Write-Log "Step 1: Sincronizando Alteracoes com o GitHub..." "INFO" "Yellow"
            Write-Progress -Activity "Deploy Supremo" -Status "Subindo atualizacoes para o GitHub..." -PercentComplete 40
            
            $pushScript = Join-Path $ScriptDir "push-to-github.ps1"
            if (Test-Path $pushScript) {
                Write-Log "Disparando push-to-github automatico..." "INFO" "Cyan"
                & powershell -ExecutionPolicy Bypass -File $pushScript
                if ($LASTEXITCODE -ne 0) {
                    throw "Falha ao sincronizar o repositorio Git local com o GitHub remoto."
                }
            } else {
                Write-Log "[!] push-to-github.ps1 nao localizado. Executando git push direto..." "WARN" "Yellow"
                & git add .
                & git commit -m "OMEGA-SUPREME V3.7: Atualizacao Estabilizada" 2>&1 | Out-Null
                & git push origin main
            }
        } else {
            Write-Log "Step 1: Sincronizacao do GitHub pulada (-SkipGitPush ativo)." "INFO" "Yellow"
        }

        $gitRemote = (git remote get-url origin 2>$null)
        if (-not $gitRemote) {
            throw "Repositorio remoto Git origin nao configurado no seu repositorio local."
        }
        $gitRemote = $gitRemote.Trim()

        # 4. Clone ou Pull na VPS
        Write-Log "" "INFO" "White"
        Write-Log "Step 2: Sincronizando fontes na VPS..." "INFO" "Yellow"
        Write-Progress -Activity "Deploy Supremo" -Status "Atualizando fontes Git na VPS..." -PercentComplete 60

        $sshCloneCmd = 'sudo mkdir -p ' + $global:vpsPath + ' && ' +
                       'sudo chown -R ' + $global:VpsUser + ' ' + $global:vpsPath + ' && ' +
                       'if [ -d ' + "'" + $global:vpsPath + '/.git' + "'" + ' ]; then ' +
                           'cd ' + $global:vpsPath + ' && git fetch --all && git reset --hard origin/main && git pull origin main; ' +
                       'elif [ -d ' + "'" + $global:vpsPath + "'" + ' ] && [ -n "$(ls -A ' + $global:vpsPath + ' 2>/dev/null)" ]; then ' +
                           'cd ' + $global:vpsPath + ' && git init && git remote add origin ' + $gitRemote + ' 2>/dev/null || git remote set-url origin ' + $gitRemote + ' && git fetch --all && git reset --hard origin/main && git branch --set-upstream-to=origin/main main 2>/dev/null || true && git pull origin main; ' +
                       'else ' +
                           'git clone ' + $gitRemote + ' ' + $global:vpsPath + '; ' +
                       'fi'
        
        Write-Log "Conectando a VPS e puxando os arquivos Git..." "INFO" "Cyan"
        $cloneRes = Invoke-SshCommand -Command $sshCloneCmd
        if ($cloneRes.ExitCode -ne 0) {
            throw ("Erro critico ao puxar fontes Git na VPS: " + $cloneRes.Output)
        }
        Write-Log "Repositorio de codigo atualizado na VPS com sucesso." "INFO" "Green"

        # 5. Copia do arquivo .env via SCP
        Write-Log "" "INFO" "White"
        Write-Log "Step 3: Enviando variaveis de ambiente (.env) via SCP..." "INFO" "Yellow"
        Write-Progress -Activity "Deploy Supremo" -Status "Copiando .env via SCP..." -PercentComplete 75

        $destEnv = $global:vpsPath + "/.env"
        $destDevopsEnv = $global:vpsPath + "/devops/.env"

        $scp1 = Invoke-ScpTransfer -LocalFilePath $localEnv -RemoteDestinationPath $destEnv
        $scp2 = Invoke-ScpTransfer -LocalFilePath $localEnv -RemoteDestinationPath $destDevopsEnv

        if ($scp1 -ne 0 -or $scp2 -ne 0) {
            throw "Erro critico ao transferir arquivos .env via SCP."
        }
        Write-Log "Arquivo .env local transferido com seguranca para a VPS." "INFO" "Green"

        # 6. Docker Compose e SSL na VPS
        Write-Log "" "INFO" "White"
        Write-Log "Step 4: Compilando e orquestrando containers Docker..." "INFO" "Yellow"
        Write-Progress -Activity "Deploy Supremo" -Status "Executando orquestracao Docker Compose e SSL..." -PercentComplete 90

        $sshDeployCmd = "cd " + $global:vpsPath + " && chmod +x devops/scripts/deploy_docker_compose.sh && ./devops/scripts/deploy_docker_compose.sh"
        $deployRes = Invoke-SshCommand -Command $sshDeployCmd
        
        if ($deployRes.ExitCode -ne 0) {
            throw ("Erro na execucao do script deploy_docker_compose.sh na VPS: " + $deployRes.Output)
        }
        
        # Mostra a saida de deploy remota com cores
        $deployOutput = $deployRes.Output | Out-String
        Write-Log $deployOutput "INFO" "Gray"

        Write-Progress -Activity "Deploy Supremo" -Completed

        Write-Log "" "INFO" "White"
        Write-Log "===========================================================" "INFO" "Green"
        Write-Log "   DEPLOY REALIZADO COM COMPLETO SUCESSO!                  " "INFO" "Green"
        Write-Log " Seu PortalCursos.NG esta agora no ar e sob HTTPS seguro!   " "INFO" "Green"
        Write-Log "===========================================================" "INFO" "Green"
        Write-Log "" "INFO" "White"

        if ($global:QuietMode) {
            exit 0
        }

    } catch {
        Write-Progress -Activity "Deploy Supremo" -Completed
        Write-Log ("[ERRO] FALHA NO PROTOCOLO DE DEPLOY: " + $_.Exception.Message) "ERROR" "Red"
        if ($global:QuietMode) {
            exit 1
        }
    }

    if (-not $global:QuietMode) {
        Read-Host "Pressione Enter para voltar ao menu"
    }
}

# --- CONFIGURAR DADOS DA VPS ALVO ---
function Set-VpsTarget {
    if ($global:QuietMode) { return }
    Show-DeployHeader
    Write-Log "[CONFIG] Insira os dados de conexao da VPS:" "INFO" "Yellow"
    
    $ip = Read-Host "Digite o endereco IP publico da sua VPS"
    if ([string]::IsNullOrWhiteSpace($ip)) {
        Write-Log "[ERRO] O endereco IP e obrigatorio." "ERROR" "Red"
        Start-Sleep -Seconds 2
        return
    }
    $global:VpsIp = $ip.Trim()

    $user = Read-Host "Digite o usuario SSH (Pressione Enter para 'root')"
    if (-not [string]::IsNullOrWhiteSpace($user)) {
        $global:VpsUser = $user.Trim()
    } else {
        $global:VpsUser = "root"
    }

    Write-Host "-----------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "Caminho do arquivo de chave privada SSH local (Ex: C:\Users\nome\.ssh\id_rsa)" -ForegroundColor White
    Write-Host "ATENCAO: Se voce utiliza SENHA na VPS, apenas pressione ENTER abaixo." -ForegroundColor Yellow
    Write-Host "          (NAO DIGITE A SUA SENHA DA VPS NESTE CAMPO!)" -ForegroundColor Yellow
    Write-Host "-----------------------------------------------------------" -ForegroundColor Cyan
    
    $key = Read-Host "Caminho da chave privada"
    if (-not [string]::IsNullOrWhiteSpace($key)) {
        $global:PrivateKeyPath = $key.Trim()
    } else {
        $global:PrivateKeyPath = ""
    }

    Write-Log "[OK] VPS Alvo configurada com sucesso no painel!" "INFO" "Green"
    Start-Sleep -Seconds 1
}

# --- TESTAR CONEXAO SSH IMEDIATA ---
function Test-ImmediateConnection {
    if ($global:QuietMode) { return }
    Show-DeployHeader
    if (-not $global:VpsIp) {
        Write-Log "[ERRO] Configure a VPS Alvo (Opcao 1) antes de testar a conexao." "ERROR" "Red"
        Start-Sleep -Seconds 2
        return
    }

    Write-Log ("Conectando a VPS " + $global:VpsIp + " via SSH...") "INFO" "Yellow"
    $res = Invoke-SshCommand -Command "echo 'SSH_CONNECTION_SUCCESS'"

    if ($res.ExitCode -eq 0 -and $res.Output -match "SSH_CONNECTION_SUCCESS") {
        Write-Log "[SUCESSO] Login SSH realizado perfeitamente!" "INFO" "Green"
    } else {
        Write-Log ("[ERRO] Falha ao conectar via SSH: " + $res.Output) "ERROR" "Red"
        Write-Log "  Verifique suas chaves locais, senha ou as portas no firewall." "WARN" "Yellow"
    }
    
    Write-Host ""
    Read-Host "Pressione Enter para voltar ao menu"
}

# --- EXECUCAO EM MODO NAO INTERATIVO (CI/CD) ---
if ($global:QuietMode) {
    Write-Log "Modo Silencioso / CI-CD Detectado. Disparando Deploy Direto..." "INFO" "Cyan"
    Invoke-SupremeDeploy
    exit
}

# --- LOOP PRINCIPAL DO MENU INTERATIVO ---
while ($true) {
    Show-DeployHeader
    Write-Host "   1. [CONFIG]      Definir IP, Usuario e Chave SSH da VPS" -ForegroundColor White
    Write-Host "   2. [SSH-KEYS]    Gerar/Instalar Chave SSH local na VPS " -ForegroundColor White
    Write-Host "   3. [VERIFICAR]   Rodar Verificacao de Dependencias locais" -ForegroundColor White
    Write-Host "   4. [CONEXAO]     Testar Acesso SSH Imediato a VPS      " -ForegroundColor White
    Write-Host "   5. [LAUNCH]      Executar Deploy Supremo V3.7 (Docker) " -ForegroundColor Green
    Write-Host "   6. [SAIR]        Sair do Painel de Automacao           " -ForegroundColor Red
    Write-Host "===========================================================" -ForegroundColor Cyan
    
    $choice = Read-Host "Selecione uma opcao (1-6)"
    
    switch ($choice) {
        "1" { Set-VpsTarget }
        "2" { Install-LocalSshKeyToVps }
        "3" { 
            # Executa o verify-deploy local
            $verifyScript = Join-Path $ScriptDir "verify-deploy.ps1"
            if (Test-Path $verifyScript) {
                & powershell -ExecutionPolicy Bypass -File $verifyScript
            } else {
                Write-Log "[ERRO] Script verify-deploy.ps1 nao encontrado na raiz." "ERROR" "Red"
            }
            Write-Host ""
            Read-Host "Pressione Enter para voltar ao menu"
        }
        "4" { Test-ImmediateConnection }
        "5" { Invoke-SupremeDeploy }
        "6" { 
            Write-Log "Encerrando orquestrador PortalCursos.NG. Ate breve!" "INFO" "Cyan"
            exit 0
        }
        default {
            Write-Log "[ALERTA] Opcao invalida. Selecione de 1 a 6." "WARN" "Yellow"
            Start-Sleep -Seconds 1
        }
    }
}
