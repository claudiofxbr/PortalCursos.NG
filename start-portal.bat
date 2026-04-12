@echo off
TITLE PORTAL CURSOS NG - INICIAR PORTAL
echo [SUPREME] Iniciando Orquestrador Maestro...
powershell -NoProfile -ExecutionPolicy Bypass -File "./start-portal.ps1"
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha ao iniciar o portal.
    pause
)
