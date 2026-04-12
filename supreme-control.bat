@echo off
TITLE PORTAL CURSOS NG - PAINEL SUPREMO V30.0
echo [SUPREME] Iniciando Interface de Controle Universal...
powershell -NoProfile -ExecutionPolicy Bypass -File "./supreme-control.ps1"
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha ao iniciar o PowerShell. 
    echo Verifique se o PowerShell esta instalado e acessivel no seu sistema.
    pause
)
