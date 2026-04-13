# PROTOCOLO V38.9-ULTRA: Reinicialização Definitiva com Código Corrigido
# Execute este script como ADMINISTRADOR para garantir permissões de encerramento de processo

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "   REINICIALIZACAO CAMPUS CARE V38.9     " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Encerrar o backend atual (código antigo com @Where)
Write-Host "[1/4] Encerrando backend antigo (PID registrado)..." -ForegroundColor Yellow
$pidFile = "$PSScriptRoot\portal.pid"
if (Test-Path $pidFile) {
    $oldPid = Get-Content $pidFile -Raw -ErrorAction SilentlyContinue
    if ($oldPid) {
        $oldPid = $oldPid.Trim()
        Write-Host "      Encerrando processo PID: $oldPid" -ForegroundColor Gray
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
        taskkill /F /PID $oldPid 2>$null | Out-Null
        Start-Sleep -Seconds 2
    }
}

# 2. Limpar processos Java na porta 8080 que possam ter ficado
Write-Host "[2/4] Liberando porta 8080..." -ForegroundColor Yellow
$pids = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($p in $pids) {
    Write-Host "      Liberando porta de processo $p..." -ForegroundColor Gray
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 3

# 3. Detectar Java
Write-Host "[3/4] Detectando Java 17..." -ForegroundColor Yellow
$javaFound = $false
$jdkPaths = @(
    "C:\Program Files\Amazon Corretto\jdk17.0.18_9\bin\java.exe",
    "C:\Program Files\Amazon Corretto\jdk17.0.10_7\bin\java.exe",
    "C:\Program Files\Java\jdk-17\bin\java.exe"
)
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
    Write-Host "ERRO: Java 17 nao encontrado." -ForegroundColor Red
    pause
    exit 1
}
Write-Host "      Java detectado em: $env:JAVA_HOME" -ForegroundColor Green

# 4. Compilar e iniciar com novo código (sem @Where - corrigido para Campus Care)
Write-Host "[4/4] Compilando e iniciando Backend V38.9..." -ForegroundColor Yellow
Write-Host "      Aguarde - isto pode levar alguns minutos..." -ForegroundColor Gray
Write-Host ""
Set-Location -Path "$PSScriptRoot"

if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run "-DskipTests" 2>&1 | Tee-Object -FilePath "$PSScriptRoot\startup_v389.log"
} else {
    mvn spring-boot:run "-DskipTests" 2>&1 | Tee-Object -FilePath "$PSScriptRoot\startup_v389.log"
}

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "FALHA: Backend nao iniciou corretamente. Verifique startup_v389.log" -ForegroundColor Red
}
pause
