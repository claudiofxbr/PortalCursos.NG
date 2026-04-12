# PortalCursos.NG - Script OI Supremo V38.2
# Atalho de Alta Performance para o Ecossistema Institucional

Clear-Host
$host.UI.RawUI.WindowTitle = "PortalCursos.NG - OI SUPREMO"

Write-Host "===========================================================" -ForegroundColor Magenta
Write-Host "      PORTAL CURSOS NG - COMANDO OI (ROOT ACCESS)         " -ForegroundColor White
Write-Host "===========================================================" -ForegroundColor Magenta
Write-Host "[SISTEMA] Verificando integridade..." -ForegroundColor Gray

# 1. Verificar se estamos na raiz correta
if (-not (Test-Path "backend") -or -not (Test-Path "frontend")) {
    Write-Host "[ERRO] Execute este comando na raiz do projeto!" -ForegroundColor Red
    exit
}

# 2. Carregar o Painel de Controle
if (Test-Path ".\supreme-control.ps1") {
    Write-Host "[OK] Orquestrador V38.1 localizado. Iniciando..." -ForegroundColor Green
    Start-Sleep -Milliseconds 500
    powershell -NoProfile -ExecutionPolicy Bypass -File ".\supreme-control.ps1"
} else {
    Write-Host "[ERRO] Script de controle supremo nao encontrado!" -ForegroundColor Red
}
