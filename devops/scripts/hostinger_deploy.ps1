# =================================================================
# SCRIPT DE GERENCIAMENTO DE DEPLOY - PortalCursos.NG
# Destino: Execução via Windows PowerShell para Hostinger VPS
# =================================================================

$VpsIp = Read-Host "Digite o IP da sua VPS Hostinger"
$VpsUser = "root" # Altere se usar outro usuário

Write-Host "🚀 Iniciando processo de deploy remoto..." -ForegroundColor Cyan

# 1. Enviar o script Linux para a VPS usando SCP
Write-Host "📦 Enviando script de automação para o servidor..." -ForegroundColor Yellow
scp devops/scripts/hostinger_deploy.sh "${VpsUser}@${VpsIp}:/tmp/hostinger_deploy.sh"

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao enviar o arquivo. Verifique se o IP está correto e se você tem acesso SSH." -ForegroundColor Red
    exit
}

# 2. Executar o script remotamente via SSH
Write-Host "⚙️  Executando configuração no servidor Linux..." -ForegroundColor Yellow
ssh "${VpsUser}@${VpsIp}" "chmod +x /tmp/hostinger_deploy.sh && /tmp/hostinger_deploy.sh"

Write-Host "✅ Processo concluído! Verifique as mensagens acima para próximos passos." -ForegroundColor Green
