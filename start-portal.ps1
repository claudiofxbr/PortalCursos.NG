# PortalCursos.NG: Orquestrador Maestro V30.0-SUPREME (Diagnostic Edition)

Clear-Host
echo "==========================================================="
echo "   PORTAL CURSOS NG - ORQUESTRADOR MAESTRO V30.0-SUPREME  "
echo "==========================================================="

# Verificacao de Diretorio
if (-not (Test-Path "backend") -or -not (Test-Path "frontend")) {
    echo "ERRO CRITICO: Execute este script na raiz do projeto 'PortalCursos.NG'."
    echo "Uso correto: ./start-portal.ps1"
    exit
}

# Carregar Variaveis de Ambiente do backend/.env para heranca no Start-Process
$envFile = Join-Path $PSScriptRoot "backend\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^(?<name>[^#\s=]+)=(?<value>.*)$") {
            $name = $matches.name
            $value = $matches.value.Trim("'").Trim('"')
            [System.Environment]::SetEnvironmentVariable($name, $value)
            Set-Item -Path "env:\$name" -Value $value
        }
    }
}


# 1. Pre-Flight Check: Infraestrutura Neon
echo "[V30.0] Verificando conectividade com Neon Cloud..."
$neonHost = "ep-small-shadow-acm4l09l-pooler.sa-east-1.aws.neon.tech"
$neonPort = 5432
try {
    $tcpCheck = Test-NetConnection -ComputerName $neonHost -Port $neonPort -ErrorAction SilentlyContinue
    if (-not $tcpCheck.TcpTestSucceeded) {
        echo "ALERTA: O Banco de Dados Neon não parece estar acessível."
        echo "Verifique sua conexão de rede ou se o serviço Neon está ativo."
        $confirm = Read-Host "Deseja tentar iniciar mesmo assim? (S/N)"
        if ($confirm -ne "S") { exit }
    } else {
        echo "[V30.0] Confirmado: Tunel de rede para Neon OK."
    }
} catch {
    echo "Aviso: Não foi possível testar a rede (Ignorado)."
}

# 2. Limpeza de Portas (Auto-Healing)
echo "[V30.0] Verificando integridade das portas 8080 e 3000..."
$ports = @(8080, 3000)
foreach ($port in $ports) {
    # Comando mais robusto para Windows 11
    $proc = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($proc) {
        $pidToKill = $proc.OwningProcess
        echo "[V30.0] Liberando porta $port (Processo: $pidToKill)..."
        Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        taskkill /F /PID $pidToKill /T 2>$null
    }
}

# 3. Boot do Backend (Interface com Neon)
echo "[V30.0] Despertando Backend e Banco Neon..."
$backendPath = Join-Path $PSScriptRoot "backend"
if (Test-Path $backendPath) {
    Start-Process powershell -ArgumentList "-NoExit", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "cd '$backendPath'; .\start-portal.ps1" -WindowStyle Normal
} else {
    echo "ERRO: Pasta 'backend' não encontrada em $backendPath"
}

# 4. Sincronizacao de Saude (Health Check)
echo "[V30.0] Aguardando estabilizacao da infraestrutura..."
$maxAttempts = 40 # Aumentado para 120s (Protocolo V30.0)
$attempt = 1
$healthUrl = "http://localhost:8080/api/health"
$ready = $false

while ($attempt -le $maxAttempts -and -not $ready) {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -Method Get -TimeoutSec 5 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 202) {
            echo "`n[V30.0] SUCESSO: Backend Ativo e Banco Sincronizado!"
            $ready = $true
        }
    } catch {
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 3
        $attempt++
    }
}

if (-not $ready) {
    echo "`n[!] ALERTA: O Backend está demorando além do normal."
}

# 5. Boot do Frontend
echo "[V30.0] Lancando Interface de Usuario (Next.js)..."
$frontendPath = Join-Path $PSScriptRoot "frontend"
if (Test-Path $frontendPath) {
    # Evitar que o Next.js herde a variável PORT=8080 carregada do .env do backend
    $env:PORT = "3000"
    Start-Process "npm.cmd" -ArgumentList "run dev -- -p 3000" -WorkingDirectory $frontendPath -WindowStyle Normal
}

echo "`n[V30.0] AMBIENTE PRONTO E ESTABILIZADO!"
echo "-----------------------------------------------------------"
echo "URL Local: http://localhost:3000"
echo "-----------------------------------------------------------"

# Abertura do Navegador
Start-Sleep -Seconds 3
Start-Process "http://localhost:3000"

# Fim
