# PortalCursos.NG: Orquestrador Maestro V21.1-ULTRA (Robust Edition)

Clear-Host
echo "==========================================================="
echo "   PORTAL CURSOS NG - ORQUESTRADOR MAESTRO V21.1-ULTRA    "
echo "==========================================================="

# Verificacao de Diretorio
if (-not (Test-Path "backend") -or -not (Test-Path "frontend")) {
    echo "ERRO CRITICO: Execute este script na raiz do projeto 'PortalCursos.NG'."
    echo "Uso correto: ./start-portal.ps1"
    exit
}

# 1. Limpeza de Portas (Auto-Healing)
echo "[V21.1] Verificando integridade das portas 8080 e 3000..."
$ports = @(8080, 3000)
foreach ($port in $ports) {
    $proc = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($proc) {
        echo "[V21.1] Liberando porta $port (Processo: $($proc.OwningProcess))..."
        Stop-Process -Id $proc.OwningProcess -Force -ErrorAction SilentlyContinue
        if ($port -eq 8080) {
            # Limpeza profunda para Java zumbi no Windows
            taskkill /F /FI "PID eq $($proc.OwningProcess)" /T 2>$null
        }
    }
}

# 2. Boot do Backend (Interface com Neon)
echo "[V22.2] Despertando Backend e Banco Neon..."
$backendPath = Join-Path $PSScriptRoot "backend"
if (Test-Path $backendPath) {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendPath'; .\mvnw.cmd spring-boot:run" -WindowStyle Normal
} else {
    echo "ERRO: Pasta 'backend' não encontrada em $backendPath"
}

# 3. Sincronizacao de Saude (Health Check)
echo "[V22.2] Aguardando estabilizacao da infraestrutura..."
$maxAttempts = 30 # Aumentado para tolerar Cold Start do Neon
$attempt = 1
$healthUrl = "http://localhost:8080/api/health"
$ready = $false

while ($attempt -le $maxAttempts -and -not $ready) {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -Method Get -TimeoutSec 5 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 202) {
            echo "`n[V22.2] SUCESSO: Backend Ativo (Status: $($response.StatusCode))!"
            $ready = $true
        }
    } catch {
        Write-Host "($attempt..)" -NoNewline
        Start-Sleep -Seconds 3
        $attempt++
    }
}

if (-not $ready) {
    echo "`n[!] ALERTA: O Backend está demorando. Verifique a janela do Maven."
}

# 4. Boot do Frontend
echo "[V22.2] Lancando Interface de Usuario (Turbopack)..."
$frontendPath = Join-Path $PSScriptRoot "frontend"
if (Test-Path $frontendPath) {
    Start-Process "npm.cmd" -ArgumentList "run dev" -WorkingDirectory $frontendPath -WindowStyle Normal
} else {
    echo "ERRO: Pasta 'frontend' não encontrada em $frontendPath"
}

echo "`n[V22.2] AMBIENTE PRONTO E ESTABILIZADO!"
echo "-----------------------------------------------------------"
echo "URL Local: http://localhost:3000"
echo "-----------------------------------------------------------"

# Abertura do Navegador
Start-Sleep -Seconds 3
Start-Process "http://localhost:3000"

# Fim
