# fix_env_vps.ps1 - Corrige o .env na VPS e reinicia o backend
# Execute: .\devops\scripts\fix_env_vps.ps1

$VpsUser = "root"
$VpsHost = "xavierbr-vps.tech"
$SshOpts = @("-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=30")

Write-Host ""
Write-Host "  CORRECAO DO .env NA VPS" -ForegroundColor Cyan
Write-Host "  Host: ${VpsUser}@${VpsHost}" -ForegroundColor Cyan
Write-Host ""

# --- Script remoto que corrige apenas as linhas erradas ---
$RemoteScript = @'
set -e
ENV="/var/www/portalcursos/.env"

echo "=== ANTES ==="
grep "^SPRING_DATASOURCE" "$ENV" | sed 's/PASSWORD=.*/PASSWORD=[OCULTO]/'

# Corrige URL (tinha placeholder ep-xxxx)
sed -i 's|^SPRING_DATASOURCE_URL=.*|SPRING_DATASOURCE_URL=jdbc:postgresql://ep-steep-recipe-ac2stduw-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require|' "$ENV"

# Corrige PASSWORD (tinha a URL inteira no lugar da senha)
sed -i 's|^SPRING_DATASOURCE_PASSWORD=postgresql://.*|SPRING_DATASOURCE_PASSWORD=npg_QifzW3pTLVR0|' "$ENV"

# Adiciona variaveis ausentes se nao existirem
grep -q "^NEXT_PUBLIC_BASE_PATH=" "$ENV" || echo "NEXT_PUBLIC_BASE_PATH=/portalcursos.ng" >> "$ENV"
grep -q "^APP_SECURE_COOKIES="    "$ENV" || echo "APP_SECURE_COOKIES=true"               >> "$ENV"

echo ""
echo "=== DEPOIS ==="
grep "^SPRING_DATASOURCE" "$ENV" | sed 's/PASSWORD=.*/PASSWORD=[OK-OCULTO]/'
echo "JWT presente: $(grep -q "^APP_JWT_SECRET=." "$ENV" && echo SIM || echo NAO)"
echo ""

# Reinicia o backend com o .env corrigido
echo "=== REINICIANDO BACKEND ==="
cd /var/www/portalcursos
docker compose -f devops/docker-compose.prod.yml --env-file "$ENV" restart backend
echo "Aguardando 40s para o Spring Boot inicializar..."
sleep 40

echo ""
echo "=== HEALTH CHECK ==="
curl -sf http://127.0.0.1:8090/api/health && echo "" || echo "backend ainda nao respondeu"

echo ""
echo "=== LOGS BACKEND (ultimas 20 linhas) ==="
docker logs portalcursos_backend --tail 20
'@

Write-Host "Executando correcao na VPS..." -ForegroundColor Yellow
Write-Host ""

$SshArgs = $SshOpts + @("${VpsUser}@${VpsHost}", $RemoteScript)
& ssh @SshArgs
$ExitCode = $LASTEXITCODE

Write-Host ""
if ($ExitCode -eq 0) {
    Write-Host "  Correcao aplicada." -ForegroundColor Green
    Write-Host "  Acesse: https://xavierbr-vps.tech/portalcursos.ng" -ForegroundColor Green
} else {
    Write-Host "  Erro (codigo $ExitCode). Verifique os logs acima." -ForegroundColor Red
}
Write-Host ""
