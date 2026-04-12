@echo off
TITLE PORTAL CURSOS NG - PUBLICAR GITHUB
echo [SUPREME] Iniciando Sincronizacao GitHub...
powershell -NoProfile -ExecutionPolicy Bypass -File "./push-to-github.ps1"
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha na sincronizacao.
    pause
)
