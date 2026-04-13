# PortalCursos.NG: Painel de Controle Supremo V30.9-SUPREME-FINAL
# Orquestrador Universal - Protocolo de Robustez

function Show-Menu {
    Clear-Host
    echo "==========================================================="
    echo "   PORTAL CURSOS NG - PAINEL SUPREMO V38.2-ROOT           "
    echo "   Acesso: ROOTMASTER | Status: RESILIENTE (FIX-ROLES)    "
    echo "==========================================================="
    echo "   1. [EXECUTAR] Iniciar Portal (Backend + Frontend)       "
    echo "   2. [GITHUB]   Sincronizar e Enviar para GitHub         "
    echo "   3. [TESTE]    Verificar Conexao Neon PostgreSQL        "
    echo "   4. [INFO]     Verificar Logs de Telemetria             "
    echo "   5. [SAIR]     Encerrar Painel                          "
    echo "==========================================================="
    echo "DICA: Use o comando 'Oi' ou 'GO' no terminal para este painel."
}

while ($true) {
    Show-Menu
    $choice = Read-Host "Selecione uma opção (1-5)"
    
    switch ($choice) {
        "1" {
            echo "`n[SUPREME] Verificando scripts de inicializacao..."
            if (Test-Path "./start-portal.ps1") {
                powershell -ExecutionPolicy Bypass -File "./start-portal.ps1"
            } else {
                echo "[ERRO] Arquivo 'start-portal.ps1' nao encontrado!"
            }
            Pause
        }
        "2" {
            echo "`n[SUPREME] Iniciando sincronizacao GitHub..."
            if (Test-Path "./push-to-github.ps1") {
                powershell -ExecutionPolicy Bypass -File "./push-to-github.ps1"
            } else {
                echo "[ERRO] Arquivo 'push-to-github.ps1' nao encontrado!"
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
            echo "`n[INFO] Exibindo últimas entradas de telemetria..."
            # Simulação de log se o banco estiver offline, ou leitura local se houver
            Get-Content "backend/logs/telemetry.log" -ErrorAction SilentlyContinue | Select-Object -Last 10
            echo "Nota: Logs detalhados estão no arquivo 'connectivity_report.md'."
            Pause
        }
        "5" {
            echo "Encerrando. Até logo!"
            exit
        }
        default {
            echo "Opção inválida. Tente novamente."
            Start-Sleep -Seconds 1
        }
    }
}
