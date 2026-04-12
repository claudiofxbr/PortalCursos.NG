@echo off
REM =================================================================
REM PORTAL CURSOS NG - ORQUESTRADOR UNIVERSAL GO
REM = [ PROTOCOLO V30.9-SUPREME ] =
REM =================================================================
SETLOCAL

echo [SUPREME] Verificando ambiente...
where powershell >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERRO] PowerShell nao encontrado. Instale o PowerShell para continuar.
    pause
    exit /b 1
)

echo [SUPREME] Iniciando Painel de Controle...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\supreme-control.ps1"

if %errorlevel% neq 0 (
    echo.
    echo [AVISO] O Painel foi encerrado com codigo %errorlevel%
    pause
)

ENDLOCAL
