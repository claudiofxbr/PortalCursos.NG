# PortalCursos.NG - Script OI Supremo V38.9-CAMPUS-CARE
# Atalho de Alta Performance para o Ecossistema Institucional
# ATUALIZADO: Integração com reinicialização do Campus Care

Clear-Host
$host.UI.RawUI.WindowTitle = "PortalCursos.NG - OI SUPREMO V38.9"

Write-Host "===========================================================" -ForegroundColor Magenta
Write-Host "      PORTAL CURSOS NG - COMANDO OI (ROOT ACCESS)          " -ForegroundColor White
Write-Host "      CAMPUS CARE V38.9 - INFRAESTRUTURA INTEGRADA         " -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Magenta
Write-Host "[SISTEMA] Verificando integridade do ecossistema..." -ForegroundColor Gray

# 1. Verificar se estamos na raiz correta (usando $PSScriptRoot para robustez)
$backendPath  = Join-Path $PSScriptRoot "backend"
$frontendPath = Join-Path $PSScriptRoot "frontend"

if (-not (Test-Path $backendPath) -or -not (Test-Path $frontendPath)) {
    Write-Host "[ERRO] Estrutura do projeto invalida. Execute na raiz do PortalCursos.NG!" -ForegroundColor Red
    Write-Host "       Caminho esperado: $PSScriptRoot" -ForegroundColor Yellow
    pause
    exit
}

Write-Host "[OK] Estrutura validada: Backend e Frontend localizados." -ForegroundColor Green

# 2. Carregar o Painel de Controle Supremo
$controlScript = Join-Path $PSScriptRoot "supreme-control.ps1"
if (Test-Path $controlScript) {
    Write-Host "[OK] Orquestrador V38.9 localizado. Iniciando painel..." -ForegroundColor Green
    Start-Sleep -Milliseconds 500
    powershell -NoProfile -ExecutionPolicy Bypass -File $controlScript
} else {
    Write-Host "[ERRO] Script de controle supremo nao encontrado em: $controlScript" -ForegroundColor Red
    pause
}
