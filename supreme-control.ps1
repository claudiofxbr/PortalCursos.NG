# PortalCursos.NG: Painel de Controle Supremo V38.9-CAMPUS-CARE
# Orquestrador Universal - Protocolo de Robustez

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

function Show-Menu {
    Clear-Host
    echo "==========================================================="
    echo "   PORTAL CURSOS NG - PAINEL SUPREMO V38.9-ROOT           "
    echo "   Acesso: ROOTMASTER | Status: RESILIENTE (CAMPUS-CARE)  "
    echo "==========================================================="
    echo "   1. [EXECUTAR]  Iniciar Portal (Backend + Frontend)      "
    echo "   2. [GITHUB]    Sincronizar e Enviar para GitHub         "
    echo "   3. [TESTE]     Verificar Conexao Neon PostgreSQL        "
    echo "   4. [INFO]      Verificar Logs de Telemetria             "
    echo "   6. [SAIR]      Encerrar Painel                          "
    echo "-----------------------------------------------------------"
    echo "   5. [CAMPUS CARE] Reiniciar Backend com Correcoes        "
    echo "      (Resolver Erro 500 no modulo de Infraestrutura)      "
    echo "==========================================================="
    echo "DICA: Use o comando 'Oi' ou 'GO' no terminal para este painel."
}

while ($true) {
    Show-Menu
    $choice = Read-Host "Selecione uma opcao (1-6)"
    
    switch ($choice) {
        "1" {
            Clear-Host
            echo "==========================================================="
            echo "   INICIANDO ECOSSISTEMA PORTALCURSOS V38.9               "
            echo "   Backend (Spring Boot) + Frontend (Next.js)             "
            echo "==========================================================="
            echo ""

            # --- DETECCAO JAVA ---
            $javaEnvSet = $false
            $jdkPaths = @(
                "C:\Program Files\Amazon Corretto\jdk17.0.18_9\bin\java.exe",
                "C:\Program Files\Amazon Corretto\jdk17.0.10_7\bin\java.exe",
                "C:\Program Files\Java\jdk-17\bin\java.exe"
            )
            $whereJava = where.exe java 2>$null | Select-Object -First 1
            if ($whereJava) {
                $env:JAVA_HOME = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($whereJava))
                $javaEnvSet = $true
            } else {
                foreach ($jdkPath in $jdkPaths) {
                    if (Test-Path $jdkPath) {
                        $javaBinDir = [System.IO.Path]::GetDirectoryName($jdkPath)
                        $env:JAVA_HOME = [System.IO.Path]::GetDirectoryName($javaBinDir)
                        $env:Path = "$javaBinDir;" + $env:Path
                        $javaEnvSet = $true
                        break
                    }
                }
            }
            if (-not $javaEnvSet) {
                Write-Host "[ERRO] Java 17 nao encontrado. Instale o Amazon Corretto 17." -ForegroundColor Red
                Pause
                break
            }
            Write-Host "[OK] Java 17: $env:JAVA_HOME" -ForegroundColor Green

            # --- LIBERAR PORTAS ---
            Write-Host "[...] Liberando portas 8080 e 3000..." -ForegroundColor Yellow
            foreach ($porta in @(8080, 3000)) {
                $portPids = Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue |
                            Select-Object -ExpandProperty OwningProcess -Unique
                foreach ($p in $portPids) {
                    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                }
            }
            Start-Sleep -Seconds 2

            # --- BACKEND: Spring Boot em janela separada ---
            $backendDir  = Join-Path $PSScriptRoot "backend"
            $backendLog  = Join-Path $backendDir "startup_log.txt"
            $mvnwPath    = Join-Path $backendDir "mvnw.cmd"
            $mvnCmd      = if (Test-Path $mvnwPath) { ".\mvnw.cmd" } else { "mvn" }

            Write-Host "[1/2] Iniciando Backend (Spring Boot)..." -ForegroundColor Cyan
            Start-Process powershell -ArgumentList @(
                "-NoExit", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                "Set-Location '$backendDir'; .\start-portal.ps1 2>&1 | Tee-Object -FilePath '$backendLog'"
            ) -WindowStyle Normal
            Write-Host "      Backend iniciando... aguarde ~30s para estar pronto." -ForegroundColor Gray

            # Aguarda um pouco antes de iniciar o frontend
            Start-Sleep -Seconds 5

            # --- FRONTEND: Next.js em janela separada ---
            $frontendDir = Join-Path $PSScriptRoot "frontend"
            Write-Host "[2/2] Iniciando Frontend (Next.js - npm run dev)..." -ForegroundColor Cyan
            Start-Process powershell -ArgumentList @(
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                "Set-Location '$frontendDir'; npm run dev"
            ) -WindowStyle Normal
            Write-Host "      Frontend iniciando em http://localhost:3000" -ForegroundColor Gray
            echo ""
            Write-Host "[OK] Ecossistema PortalCursos.NG iniciado com sucesso!" -ForegroundColor Green
            Write-Host "     Backend:  http://localhost:8080" -ForegroundColor White
            Write-Host "     Frontend: http://localhost:3000" -ForegroundColor White
            echo ""
            Write-Host "     AGUARDE ~60 segundos para o Backend estar totalmente pronto." -ForegroundColor Yellow
            Pause
        }
        "2" {
            echo "`n[SUPREME] Iniciando sincronizacao GitHub..."
            $pushScript = Join-Path $PSScriptRoot "push-to-github.ps1"
            if (Test-Path $pushScript) {
                powershell -ExecutionPolicy Bypass -File $pushScript
            } else {
                echo "[ERRO] Arquivo 'push-to-github.ps1' nao encontrado em: $pushScript"
            }
            Pause
        }
        "3" {
            echo "`n[INFO] Testando rede com Neon Cloud..."
            $host_neon = "ep-small-shadow-acm4l09l-pooler.sa-east-1.aws.neon.tech"
            Test-NetConnection -ComputerName $host_neon -Port 5432
            Pause
        }
        "4" {
            echo "`n[INFO] Exibindo ultimas entradas de telemetria..."
            $logPath = Join-Path $PSScriptRoot "backend\logs\telemetry.log"
            Get-Content $logPath -ErrorAction SilentlyContinue | Select-Object -Last 10
            echo "Nota: Logs detalhados estao no arquivo 'connectivity_report.md'."
            Pause
        }
        "5" {
            Clear-Host
            echo "==========================================================="
            echo "   CAMPUS CARE - PROTOCOLO DE REINICIALIZACAO V38.9        "
            echo "==========================================================="
            echo ""
            echo "[AVISO] Esta opcao irá:"
            echo "  1. Encerrar o backend em execucao (codigo antigo)"
            echo "  2. Recompilar com as correcoes do Campus Care"
            echo "  3. Reiniciar o servidor Spring Boot"
            echo ""
            echo "  O modulo 'Infraestrutura' parara de dar Erro 500."
            echo ""
            $confirm = Read-Host "Confirmar reinicializacao? (S/N)"
            
            if ($confirm -eq "S" -or $confirm -eq "s") {
                # --- ENCERRAR BACKEND ATUAL ---
                echo "`n[1/4] Encerrando backend atual..."
                $pidFile = Join-Path $PSScriptRoot "backend\portal.pid"
                if (Test-Path $pidFile) {
                    $oldPid = (Get-Content $pidFile -Raw).Trim()
                    if ($oldPid) {
                        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
                        taskkill /F /PID $oldPid 2>$null | Out-Null
                        Write-Host "     Processo $oldPid encerrado." -ForegroundColor Green
                    }
                }
                
                # --- LIBERAR PORTA 8080 ---
                echo "[2/4] Liberando porta 8080..."
                $portPids = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
                foreach ($p in $portPids) {
                    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                    Write-Host "     Processo $p encerrado." -ForegroundColor Green
                }
                Start-Sleep -Seconds 3
                
                # --- DETECTAR JAVA ---
                echo "[3/4] Detectando Java 17..."
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
                    Write-Host "[ERRO] Java 17 nao encontrado." -ForegroundColor Red
                    Pause
                    break
                }
                Write-Host "     Java detectado: $env:JAVA_HOME" -ForegroundColor Green
                
                # --- INICIAR BACKEND COM NOVO CODIGO ---
                echo "[4/4] Compilando e iniciando Backend corrigido..."
                echo "     Aguarde - isto pode levar alguns minutos..."
                $backendPath = Join-Path $PSScriptRoot "backend"
                Set-Location -Path $backendPath
                
                $logFile = Join-Path $backendPath "startup_v389.log"
                
                Start-Process powershell -ArgumentList "-NoExit -NoProfile -ExecutionPolicy Bypass -Command `"Set-Location '$backendPath'; .\start-portal.ps1 2>&1 | Tee-Object -FilePath '$logFile'`"" -WindowStyle Normal
                
                Set-Location -Path $PSScriptRoot
                echo ""
                Write-Host "[OK] Backend sendo reiniciado em nova janela!" -ForegroundColor Green
                echo "     Aguarde ~60s e acesse o Campus Care novamente."
                echo "     Log em: backend\startup_v389.log"
            } else {
                echo "Operacao cancelada."
            }
            Pause
        }
        "6" {
            echo "Encerrando. Ate logo!"
            exit
        }
        default {
            echo "Opcao invalida. Tente novamente."
            Start-Sleep -Seconds 1
        }
    }
}
