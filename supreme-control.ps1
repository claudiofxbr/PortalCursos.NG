# PortalCursos.NG: Painel de Controle Supremo V30.0-SUPREME
# Desenvolvido para estabilidade total e automação simplificada.

function Show-Menu {
    Clear-Host
    echo "==========================================================="
    echo "   PORTAL CURSOS NG - PAINEL SUPREMO V30.0-SUPREME        "
    echo "==========================================================="
    echo "   1. [EXECUTAR] Iniciar Portal (Backend + Frontend)       "
    echo "   2. [GITHUB]   Sincronizar e Enviar para Nuvem          "
    echo "   3. [TESTE]    Verificar Conexão Neon PostgreSQL        "
    echo "   4. [INFO]     Verificar Logs de Telemetria             "
    echo "   5. [SAIR]     Encerrar Painel                          "
    echo "==========================================================="
}

while ($true) {
    Show-Menu
    $choice = Read-Host "Selecione uma opção (1-5)"
    
    switch ($choice) {
        "1" {
            echo "`n[INFO] Iniciando orquestrador maestro..."
            ./start-portal.ps1
            Pause
        }
        "2" {
            echo "`n[INFO] Iniciando sincronização GitHub..."
            ./push-to-github.ps1
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
