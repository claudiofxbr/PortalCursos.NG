@echo off
:: PortalCursos.NG — Iniciador de Segurança GitHub V31.0
:: Este arquivo inicia o PowerShell com a política de execução ignorada para rodar o update-security.ps1.

title PortalCursos.NG — Seguranca GitHub

echo Iniciando processo de seguranca do PortalCursos.NG...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0update-security.ps1"

pause
