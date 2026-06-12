<#
.SYNOPSIS
    OMEGA: Painel de Gerenciamento Remoto da VPS - PortalCursos.NG
.DESCRIPTION
    Executa comandos Linux na VPS via SSH a partir do Windows.
    ATENÇÃO: certbot, nginx, docker, systemctl são comandos LINUX.
    Nunca devem ser digitados no PowerShell local — use este script.
.PARAMETER VpsIp
    IP público da VPS Hostinger (ex: 69.62.87.38)
.PARAMETER VpsUser
    Usuário SSH (padrão: root)
.PARAMETER PrivateKeyPath
    Caminho local para a chave privada SSH (opcional)
#>
[CmdletBinding()]
Param(
    [string]$VpsIp = "",
    [string]$VpsUser = "root",
    [string]$PrivateKeyPath = ""
)

$ErrorActionPreference = "Continue"
$OutputEncoding = [System.Text.Encoding]::UTF8

# --- Carrega IP do .env local se não fornecido ---
if (-not $VpsIp) {
    $envFile = Join-Path $PSScriptRoot ".env"
    if (Test-Path $envFile) {
        $envContent = Get-Content $envFile
        foreach ($line in $envContent) {
            if ($line -match "^VPS_IP=(.+)") { $VpsIp = $Matches[1].Trim() }
        }
    }
}

function Write-Banner {
    Clear-Host
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "  PORTALCURSOS.NG - GERENCIAMENTO REMOTO DA VPS (SSH)    " -ForegroundColor Cyan
    Write-Host "  ATENÇÃO: Todos os comandos rodam na VPS Linux via SSH   " -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Cyan
    if ($VpsIp) {
        Write-Host "  VPS: $VpsUser@$VpsIp" -ForegroundColor Green
    } else {
        Write-Host "  VPS: NÃO CONFIGURADA" -ForegroundColor Red
    }
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Invoke-Ssh {
    Param([string]$Cmd, [switch]$Silent)
    if (-not $VpsIp) { throw "IP da VPS não configurado. Use a opção 1 primeiro." }

    $args = @()
    if ($PrivateKeyPath -and (Test-Path $PrivateKeyPath)) {
        $args += "-i", $PrivateKeyPath
    }
    $args += "-o", "StrictHostKeyChecking=no"
    $args += "-o", "ConnectTimeout=15"
    $args += "$VpsUser@$VpsIp"
    $args += $Cmd

    if (-not $Silent) { Write-Host "[SSH] $Cmd" -ForegroundColor DarkGray }
    $output = & ssh $args 2>&1
    $code   = $LASTEXITCODE
    if (-not $Silent) { $output | ForEach-Object { Write-Host $_ } }
    return [PSCustomObject]@{ Output = ($output | Out-String); ExitCode = $code }
}

# --- OPÇÃO 1: Configurar IP da VPS ---
function Set-VpsIp {
    $ip = Read-Host "Digite o IP público da VPS"
    if ($ip.Trim()) { $script:VpsIp = $ip.Trim() }
    Write-Host "VPS configurada: $VpsIp" -ForegroundColor Green
    Start-Sleep 1
}

# --- OPÇÃO 2: Verificar SSL / Certbot na VPS ---
function Get-SslStatus {
    Write-Banner
    Write-Host "Verificando certificados SSL na VPS..." -ForegroundColor Yellow

    $res = Invoke-Ssh -Cmd "sudo certbot certificates 2>&1 || echo 'CERTBOT_NOT_FOUND'"

    if ($res.Output -match "CERTBOT_NOT_FOUND" -or $res.Output -match "not found") {
        Write-Host ""
        Write-Host "[INFO] Certbot não instalado na VPS. Use a opção 3 para instalar SSL." -ForegroundColor Yellow
    } elseif ($res.Output -match "No certificates found") {
        Write-Host ""
        Write-Host "[INFO] Certbot instalado, mas sem certificados. Use a opção 3 para emitir." -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "[OK] Certificados encontrados (veja acima)." -ForegroundColor Green
    }
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 3: Instalar SSL (Let's Encrypt) na VPS ---
function Install-Ssl {
    Write-Banner
    Write-Host "INSTALAÇÃO DE SSL - Let's Encrypt (Certbot)" -ForegroundColor Yellow
    Write-Host ""

    # Ler domínio do .env local
    $domain = "xavierbr-vps.tech"
    $email  = "claudiofxbr@gmail.com"
    $envFile = Join-Path $PSScriptRoot ".env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match "^DOMAIN_NAME=(.+)") { $domain = $Matches[1].Trim() }
            if ($_ -match "^EMAIL_ADDRESS=(.+)") { $email = $Matches[1].Trim() }
        }
    }

    Write-Host "Domínio: $domain" -ForegroundColor Cyan
    Write-Host "E-mail:  $email" -ForegroundColor Cyan
    Write-Host ""
    $confirm = Read-Host "Confirmar emissão do certificado SSL para '$domain'? (S/N)"
    if ($confirm.ToUpper() -ne "S") { return }

    Write-Host "Executando setup_ssl.sh na VPS..." -ForegroundColor Yellow

    # Envia e executa o script setup_ssl.sh na VPS
    $scriptSrc  = Join-Path $PSScriptRoot "devops\scripts\setup_ssl.sh"
    $scriptDest = "/tmp/setup_ssl.sh"

    # Copiar script para VPS
    $scpArgs = @()
    if ($PrivateKeyPath -and (Test-Path $PrivateKeyPath)) { $scpArgs += "-i", $PrivateKeyPath }
    $scpArgs += "-o", "StrictHostKeyChecking=no"
    $scpArgs += $scriptSrc
    $scpArgs += "$VpsUser@${VpsIp}:$scriptDest"
    & scp $scpArgs 2>&1 | Out-Null

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERRO] Falha ao copiar setup_ssl.sh para a VPS." -ForegroundColor Red
        Read-Host "Pressione Enter para voltar"
        return
    }

    $sslCmd = "chmod +x $scriptDest && sudo bash $scriptDest 2>&1 && rm -f $scriptDest"
    $res = Invoke-Ssh -Cmd $sslCmd

    if ($res.ExitCode -eq 0) {
        Write-Host ""
        Write-Host "[SUCESSO] SSL instalado com sucesso!" -ForegroundColor Green
        Write-Host "Acesse: https://$domain" -ForegroundColor Cyan
    } else {
        Write-Host ""
        Write-Host "[ERRO] Falha na instalação do SSL. Veja a saída acima." -ForegroundColor Red
        Write-Host "Dica: verifique se o DNS A de '$domain' aponta para $VpsIp" -ForegroundColor Yellow
    }
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 4: Status dos Containers Docker ---
function Get-DockerStatus {
    Write-Banner
    Write-Host "Status dos containers Docker na VPS..." -ForegroundColor Yellow
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml ps 2>&1 || docker ps -a 2>&1"
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 5: Logs do Backend ---
function Get-BackendLogs {
    Write-Banner
    Write-Host "Últimas 100 linhas do log do Backend..." -ForegroundColor Yellow
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml logs --tail=100 backend 2>&1"
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 6: Logs do Frontend ---
function Get-FrontendLogs {
    Write-Banner
    Write-Host "Últimas 100 linhas do log do Frontend..." -ForegroundColor Yellow
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml logs --tail=100 frontend 2>&1"
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 7: Reiniciar Nginx na VPS ---
function Restart-Nginx {
    Write-Banner
    Write-Host "Testando e reiniciando Nginx na VPS..." -ForegroundColor Yellow
    Invoke-Ssh -Cmd "sudo nginx -t 2>&1 && sudo systemctl reload nginx && echo 'NGINX_OK'"
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 8: Health Check completo ---
function Invoke-HealthCheck {
    Write-Banner
    Write-Host "Executando Health Check completo da VPS..." -ForegroundColor Yellow
    Write-Host ""

    $checks = @(
        @{ Label = "Nginx ativo";    Cmd = "systemctl is-active nginx 2>&1" },
        @{ Label = "Docker ativo";   Cmd = "systemctl is-active docker 2>&1" },
        @{ Label = "API /health";    Cmd = "curl -sf http://localhost:8090/api/health | head -c 200 2>&1 || echo 'API_UNREACHABLE'" },
        @{ Label = "Frontend ativo"; Cmd = "curl -sf http://localhost:3010 -o /dev/null -w '%{http_code}' 2>&1" },
        @{ Label = "SSL válido";     Cmd = "sudo certbot certificates 2>&1 | grep -E 'Domains|VALID|INVALID|Expiry' || echo 'SEM_CERTIFICADO'" },
        @{ Label = "Espaço em disco";Cmd = "df -h / | tail -1" },
        @{ Label = "Memória RAM";    Cmd = "free -h | grep Mem" }
    )

    foreach ($check in $checks) {
        Write-Host "  [$($check.Label)]" -ForegroundColor Cyan -NoNewline
        $res = Invoke-Ssh -Cmd $check.Cmd -Silent
        $out = $res.Output.Trim()
        if ($res.ExitCode -eq 0 -and $out -notmatch "UNREACHABLE|INVALID|error|Error") {
            Write-Host " → $out" -ForegroundColor Green
        } else {
            Write-Host " → $out" -ForegroundColor Red
        }
    }

    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 9: Renovar SSL manualmente ---
function Renew-Ssl {
    Write-Banner
    Write-Host "Renovando certificados SSL via Certbot na VPS..." -ForegroundColor Yellow
    Invoke-Ssh -Cmd "sudo certbot renew --nginx 2>&1 && sudo systemctl reload nginx && echo 'RENEW_OK'"
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 10: Reiniciar todos os containers ---
function Restart-AllContainers {
    Write-Banner
    Write-Host "Reiniciando todos os containers Docker na VPS..." -ForegroundColor Yellow
    $confirm = Read-Host "Isso causará breve indisponibilidade. Confirmar? (S/N)"
    if ($confirm.ToUpper() -ne "S") { return }
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml restart 2>&1"
    Write-Host ""
    Read-Host "Pressione Enter para voltar"
}

# --- OPÇÃO 11: SSH interativo (shell remoto) ---
function Open-SshShell {
    Write-Banner
    Write-Host "Abrindo sessão SSH interativa na VPS..." -ForegroundColor Yellow
    Write-Host "Digite 'exit' para voltar ao menu." -ForegroundColor DarkGray
    Write-Host ""

    $args = @()
    if ($PrivateKeyPath -and (Test-Path $PrivateKeyPath)) { $args += "-i", $PrivateKeyPath }
    $args += "-o", "StrictHostKeyChecking=no"
    $args += "$VpsUser@$VpsIp"
    & ssh $args
}

# --- LOOP PRINCIPAL ---
while ($true) {
    Write-Banner
    Write-Host "   1.  [CONFIG]     Definir IP da VPS" -ForegroundColor White
    Write-Host "   2.  [SSL-STATUS] Verificar certificados SSL na VPS" -ForegroundColor White
    Write-Host "   3.  [SSL-INSTALAR] Instalar/Emitir SSL (Let's Encrypt)" -ForegroundColor Green
    Write-Host "   4.  [DOCKER]     Ver status dos containers" -ForegroundColor White
    Write-Host "   5.  [LOGS-API]   Ver logs do Backend (Spring Boot)" -ForegroundColor White
    Write-Host "   6.  [LOGS-UI]    Ver logs do Frontend (Next.js)" -ForegroundColor White
    Write-Host "   7.  [NGINX]      Reiniciar Nginx na VPS" -ForegroundColor White
    Write-Host "   8.  [HEALTH]     Health Check completo da VPS" -ForegroundColor Cyan
    Write-Host "   9.  [SSL-RENEW]  Renovar certificado SSL" -ForegroundColor White
    Write-Host "   10. [RESTART]    Reiniciar todos os containers" -ForegroundColor Yellow
    Write-Host "   11. [SSH]        Abrir sessão SSH interativa" -ForegroundColor Magenta
    Write-Host "   0.  [SAIR]       Sair" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Cyan

    $choice = Read-Host "Selecione uma opção (0-11)"

    try {
        switch ($choice) {
            "1"  { Set-VpsIp }
            "2"  { Get-SslStatus }
            "3"  { Install-Ssl }
            "4"  { Get-DockerStatus }
            "5"  { Get-BackendLogs }
            "6"  { Get-FrontendLogs }
            "7"  { Restart-Nginx }
            "8"  { Invoke-HealthCheck }
            "9"  { Renew-Ssl }
            "10" { Restart-AllContainers }
            "11" { Open-SshShell }
            "0"  { Write-Host "Encerrando." -ForegroundColor Cyan; exit 0 }
            default { Write-Host "Opção inválida." -ForegroundColor Yellow; Start-Sleep 1 }
        }
    } catch {
        Write-Host "[ERRO] $($_.Exception.Message)" -ForegroundColor Red
        Read-Host "Pressione Enter para voltar"
    }
}
