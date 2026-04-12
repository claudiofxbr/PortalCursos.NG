# PortalCursos.NG - Atalho Supremo PowerShell
# Use: .\GO

Write-Host "[SUPREME] Iniciando Orquestrador V30.9..." -ForegroundColor Cyan
if (Test-Path ".\supreme-control.ps1") {
    powershell -NoProfile -ExecutionPolicy Bypass -File ".\supreme-control.ps1"
} else {
    Write-Error "[ERRO] Scripts de controle nao encontrados na raiz do projeto."
}
