# Protocolo ARMOR-MAVEN: Inicializador Inteligente PortalCursos.NG-02
# Autor: Antigravity AI
# Objetivo: Garantir que o Backend inicie independente da configuração global do PATH.

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "   INICIALIZADOR ROBUSTO PORTALCURSOS    " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Detecção Inteligente do Java 17
Write-Host "--- Verificando Ambiente ---" -ForegroundColor Yellow
$javaFound = $false

# Locais comuns de instalação do Amazon Corretto e outros JDKs
$jdkPaths = @(
    "C:\Program Files\Amazon Corretto\jdk17.0.18_9\bin\java.exe",
    "C:\Program Files\Amazon Corretto\jdk17.0.10_7\bin\java.exe",
    "C:\Program Files\Java\jdk-17\bin\java.exe"
)

# Tenta encontrar via comando 'where' se estiver no path
$whereJava = where.exe java 2>$null | Select-Object -First 1
if ($whereJava) {
    $env:JAVA_HOME = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($whereJava))
    $javaFound = $true
} else {
    foreach ($path in $jdkPaths) {
        if (Test-Path $path) {
            $env:JAVA_HOME = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($path))
            $env:Path = "$([System.IO.Path]::GetDirectoryName($path));" + $env:Path
            $javaFound = $true
            break
        }
    }
}

if (-not $javaFound) {
    Write-Host "❌ ERRO: Java 17 não encontrado. Por favor, instale o Amazon Corretto 17." -ForegroundColor Red
    pause
    exit
}

Write-Host "[OK] Java 17 Detectado: $env:JAVA_HOME" -ForegroundColor Green

# 1.5 Carregar Variáveis de Ambiente (.env)
if (Test-Path ".env") {
    Write-Host "--- Carregando Variáveis do .env ---" -ForegroundColor Yellow
    Get-Content .env | ForEach-Object {
        if ($_ -match "^(?<name>[^#\s=]+)=(?<value>.*)$") {
            $name = $matches.name
            $value = $matches.value.Trim("'").Trim('"')
            [System.Environment]::SetEnvironmentVariable($name, $value)
            Write-Host "Set $name" -ForegroundColor DarkGray
        }
    }
    Write-Host "[OK] Variáveis carregadas." -ForegroundColor Green
}

# 2. Gerenciamento de Portas (Cleanup da 8080)
Write-Host "--- Verificando Porta 8080 ---" -ForegroundColor Yellow
$pids = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($pids) {
    foreach ($p in $pids) {
        Write-Host "Limpando porta 8080 (Processo $p)..." -ForegroundColor Cyan
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        taskkill /F /PID $p 2>$null | Out-Null
    }
}

# 3. Execução via Maven Wrapper (ARMOR-MAVEN)
Write-Host "--- Iniciando Compilação e Execução ---" -ForegroundColor Yellow
Set-Location -Path "$PSScriptRoot"

if (Test-Path ".\mvnw.cmd") {
    Write-Host "Usando Maven Wrapper (Recomendado)..." -ForegroundColor Green
    .\mvnw.cmd clean spring-boot:run "-DskipTests"
} else {
    Write-Host "⚠️ Maven Wrapper não encontrado. Tentando comando global..." -ForegroundColor Yellow
    mvn clean spring-boot:run "-DskipTests"
}

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Falha crítica na execução do Backend." -ForegroundColor Red
}

pause
