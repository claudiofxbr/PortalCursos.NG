# ==============================================================================
# SCRIPT DE CONFIGURAÇÃO E INICIALIZAÇÃO - PortalCursos.NG
# ==============================================================================
# Este script automatiza o setup completo do ambiente de desenvolvimento.
# Para executar: 
# 1. Abra o PowerShell como Administrador.
# 2. Execute: Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
# 3. Rode: .\setup-and-start.ps1
# ==============================================================================

$ErrorActionPreference = "Stop" # Para a execução se algum comando falhar

function Write-Log {
    param([string]$Message, [string]$Type = "INFO")
    $Colors = @{"INFO" = "Cyan"; "SUCCESS" = "Green"; "ERROR" = "Red"; "WARN" = "Yellow"}
    Write-Host "[$Type] $(Get-Date -Format 'HH:mm:ss') - $Message" -ForegroundColor $Colors[$Type]
}

try {
    Write-Log "Iniciando Protocolo de Setup Limpo - PortalCursos.NG" "INFO"

    # 1. Limpeza de Cache
    Write-Log "Etapa 1/5: Limpando Caches e Dependências antigas..." "INFO"
    
    # Frontend
    $frontendPath = Join-Path $PSScriptRoot "frontend"
    if (Test-Path "$frontendPath\node_modules") {
        Write-Log "Removendo node_modules do frontend..." "WARN"
        Remove-Item -Recurse -Force "$frontendPath\node_modules"
    }
    if (Test-Path "$frontendPath\package-lock.json") {
        Remove-Item -Force "$frontendPath\package-lock.json"
    }
    if (Test-Path "$frontendPath\.next") {
        Remove-Item -Recurse -Force "$frontendPath\.next"
    }

    # Backend
    $backendPath = Join-Path $PSScriptRoot "backend"
    if (Test-Path "$backendPath\target") {
        Write-Log "Limpando pasta target do backend..." "WARN"
        Remove-Item -Recurse -Force "$backendPath\target"
    }

    # 2. Instalação
    Write-Log "Etapa 2/5: Instalando novas dependências..." "INFO"
    
    Write-Log "Instalando dependências do Frontend (npm)..." "INFO"
    Set-Location $frontendPath
    npm install
    
    Write-Log "Compilando Backend (Maven)..." "INFO"
    Set-Location $backendPath
    # Usando mvnw se disponível, caso contrário mvn
    if (Test-Path "mvnw.cmd") {
        .\mvnw.cmd clean install -DskipTests
    } else {
        mvn clean install -DskipTests
    }

    # 3. Inicialização de Backend
    Write-Log "Etapa 3/5: Iniciando Servidor Backend (Spring Boot)..." "INFO"
    # Inicia em uma nova janela para manter os logs visíveis e o processo em background
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd $backendPath; if (Test-Path 'mvnw.cmd') { .\mvnw.cmd spring-boot:run } else { mvn spring-boot:run }" -WindowStyle Normal
    
    Write-Log "Aguardando 15 segundos para o backend inicializar..." "INFO"
    Start-Sleep -Seconds 15

    # 4. Inicialização de Frontend
    Write-Log "Etapa 4/5: Iniciando Servidor Frontend (Next.js)..." "INFO"
    Set-Location $frontendPath
    # Inicia o frontend em uma nova janela
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd $frontendPath; npm run dev" -WindowStyle Normal

    # 5. Abertura do Navegador
    Write-Log "Etapa 5/5: Abrindo o portal no navegador..." "SUCCESS"
    Start-Sleep -Seconds 5
    Start-Process "http://localhost:3000"

    Write-Log "=====================================================" "SUCCESS"
    Write-Log "Setup Concluído! O PortalCursos.NG está rodando." "SUCCESS"
    Write-Log "Backend: http://localhost:8080" "INFO"
    Write-Log "Frontend: http://localhost:3000" "INFO"
    Write-Log "=====================================================" "SUCCESS"

} catch {
    Write-Log "FALHA CRÍTICA: $($_.Exception.Message)" "ERROR"
    exit 1
} finally {
    Set-Location $PSScriptRoot
}
