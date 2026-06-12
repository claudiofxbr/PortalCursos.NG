<#
.SYNOPSIS
    OMEGA: Painel de Gerenciamento Remoto da VPS - PortalCursos.NG
.DESCRIPTION
    Executa comandos Linux na VPS via SSH a partir do Windows.
    IMPORTANTE: certbot, nginx, docker, systemctl são comandos LINUX.
    Nunca devem ser digitados no PowerShell local — use este painel.
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

# ---------------------------------------------------------------
# Carrega configurações do .env local
# ---------------------------------------------------------------
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = Get-Location }
$EnvFile = Join-Path $ScriptDir ".env"

$script:Domain = "xavierbr-vps.tech"
$script:Email  = "claudiofxbr@gmail.com"

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match "^VPS_IP=(.+)")          { if (-not $VpsIp)          { $script:VpsIp    = $Matches[1].Trim() } }
        if ($_ -match "^DOMAIN_NAME=(.+)")      { $script:Domain = $Matches[1].Trim() }
        if ($_ -match "^EMAIL_ADDRESS=(.+)")    { $script:Email  = $Matches[1].Trim() }
    }
}
if ($VpsIp) { $script:VpsIp = $VpsIp }

# ---------------------------------------------------------------
# Utilitários
# ---------------------------------------------------------------
function Write-Banner {
    Clear-Host
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "  PORTALCURSOS.NG — GESTAO REMOTA DA VPS                 " -ForegroundColor Cyan
    Write-Host "  Todos os comandos rodam na VPS Linux via SSH            " -ForegroundColor DarkCyan
    Write-Host "==========================================================" -ForegroundColor Cyan
    if ($script:VpsIp) {
        Write-Host "  VPS   : $VpsUser@$($script:VpsIp)" -ForegroundColor Green
        Write-Host "  Dominio: $($script:Domain)" -ForegroundColor Green
    } else {
        Write-Host "  VPS   : NAO CONFIGURADA — use a opcao 1" -ForegroundColor Red
    }
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Write-Step { param([string]$Msg) Write-Host "  >> $Msg" -ForegroundColor Yellow }
function Write-Ok   { param([string]$Msg) Write-Host "  [OK] $Msg" -ForegroundColor Green }
function Write-Err  { param([string]$Msg) Write-Host "  [ERRO] $Msg" -ForegroundColor Red }
function Write-Info { param([string]$Msg) Write-Host "  $Msg" -ForegroundColor Gray }

function Invoke-Ssh {
    Param(
        [Parameter(Mandatory)][string]$Cmd,
        [switch]$Silent,
        [int]$Timeout = 120
    )
    if (-not $script:VpsIp) { throw "IP da VPS nao configurado. Use a opcao 1 primeiro." }

    $sshArgs = @()
    if ($PrivateKeyPath -and (Test-Path $PrivateKeyPath)) {
        $sshArgs += "-i", $PrivateKeyPath
    }
    $sshArgs += "-o", "StrictHostKeyChecking=no"
    $sshArgs += "-o", "ConnectTimeout=15"
    $sshArgs += "-o", "ServerAliveInterval=30"
    $sshArgs += "$VpsUser@$($script:VpsIp)"
    $sshArgs += $Cmd

    if (-not $Silent) { Write-Info "[SSH] $(($Cmd -split '\n')[0].Trim().Substring(0, [Math]::Min(80, ($Cmd -split '\n')[0].Trim().Length)))..." }

    $output   = & ssh $sshArgs 2>&1
    $exitCode = $LASTEXITCODE

    if (-not $Silent) { $output | ForEach-Object { Write-Host "  $_" } }

    return [PSCustomObject]@{ Output = ($output | Out-String).Trim(); ExitCode = $exitCode }
}

function Invoke-ScpUpload {
    Param([string]$Local, [string]$Remote)
    $scpArgs = @()
    if ($PrivateKeyPath -and (Test-Path $PrivateKeyPath)) { $scpArgs += "-i", $PrivateKeyPath }
    $scpArgs += "-o", "StrictHostKeyChecking=no"
    $scpArgs += $Local
    $scpArgs += "$VpsUser@$($script:VpsIp):$Remote"
    & scp $scpArgs 2>&1 | Out-Null
    return $LASTEXITCODE
}

function Test-SshConnection {
    if (-not $script:VpsIp) { return $false }
    $res = Invoke-Ssh -Cmd "echo OK" -Silent
    return ($res.ExitCode -eq 0 -and $res.Output -match "OK")
}

function Invoke-RemoteScript {
    Param([string]$LocalScript, [string]$Description)
    if (-not (Test-Path $LocalScript)) {
        Write-Err "Script nao encontrado localmente: $LocalScript"
        return $false
    }
    Write-Step "Enviando $Description para a VPS..."
    $remotePath = "/tmp/$([System.IO.Path]::GetFileName($LocalScript))"
    $scp = Invoke-ScpUpload -Local $LocalScript -Remote $remotePath
    if ($scp -ne 0) {
        Write-Err "Falha ao enviar script via SCP."
        return $false
    }
    Write-Step "Executando $Description na VPS..."
    $res = Invoke-Ssh -Cmd "chmod +x $remotePath && sudo bash $remotePath 2>&1; RC=`$?; rm -f $remotePath; exit `$RC"
    return ($res.ExitCode -eq 0)
}

# ---------------------------------------------------------------
# OPÇÕES DO MENU
# ---------------------------------------------------------------

function Set-VpsTarget {
    Write-Banner
    $ip = Read-Host "  Digite o IP publico da VPS (atual: $($script:VpsIp))"
    if ($ip.Trim()) {
        $script:VpsIp = $ip.Trim()
        Write-Ok "VPS configurada: $($script:VpsIp)"

        $dom = Read-Host "  Dominio (atual: $($script:Domain)) — Enter para manter"
        if ($dom.Trim()) { $script:Domain = $dom.Trim() }

        # Atualizar VPS_IP no .env local
        if (Test-Path $EnvFile) {
            $content = Get-Content $EnvFile
            if ($content -match "^VPS_IP=") {
                $content = $content -replace "^VPS_IP=.*", "VPS_IP=$($script:VpsIp)"
            } else {
                $content += "VPS_IP=$($script:VpsIp)"
            }
            Set-Content $EnvFile $content -Encoding UTF8
            Write-Ok "VPS_IP atualizado no .env local."
        }
    }
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

function Test-SshNow {
    Write-Banner
    Write-Step "Testando conexao SSH com $VpsUser@$($script:VpsIp)..."
    Write-Host ""
    $res = Invoke-Ssh -Cmd "echo 'SSH_OK' && uname -a && uptime"
    if ($res.ExitCode -eq 0 -and $res.Output -match "SSH_OK") {
        Write-Host ""
        Write-Ok "Conexao SSH estabelecida com sucesso!"
    } else {
        Write-Host ""
        Write-Err "Falha na conexao SSH. Verifique o IP, usuario e chave."
    }
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 3 — Liberar Portas 80/443 (resolve o erro de bind)
function Invoke-FixPorts {
    Write-Banner
    Write-Host "  LIBERAR PORTAS 80/443 NA VPS" -ForegroundColor Yellow
    Write-Host "  Este comando diagnostica e libera as portas ocupadas." -ForegroundColor Gray
    Write-Host "  Execute ANTES de instalar o SSL se o Nginx nao iniciar." -ForegroundColor Gray
    Write-Host ""

    if (-not (Test-SshConnection)) {
        Write-Err "Sem conexao SSH. Configure a VPS na opcao 1."
        Read-Host "  Pressione Enter para voltar"
        return
    }

    $script = Join-Path $ScriptDir "devops\scripts\fix-port443.sh"
    $ok = Invoke-RemoteScript -LocalScript $script -Description "fix-port443.sh"

    Write-Host ""
    if ($ok) {
        Write-Ok "Portas liberadas! Agora use a opcao 4 para instalar o SSL."
    } else {
        Write-Err "Falha ao liberar portas. Veja a saida acima."
        Write-Host ""
        Write-Host "  Diagnostico manual via SSH interativo (opcao 12):" -ForegroundColor Yellow
        Write-Host "    sudo ss -tlnp 'sport = :443'" -ForegroundColor DarkGray
        Write-Host "    sudo fuser 443/tcp" -ForegroundColor DarkGray
        Write-Host "    sudo systemctl status nginx" -ForegroundColor DarkGray
    }
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 4 — Instalar SSL com pré-verificação de portas
function Install-Ssl {
    Write-Banner
    Write-Host "  INSTALACAO SSL — Let's Encrypt / Certbot" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Dominio : $($script:Domain)" -ForegroundColor Cyan
    Write-Host "  E-mail  : $($script:Email)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  PRE-REQUISITOS:" -ForegroundColor White
    Write-Host "    1. DNS A de '$($script:Domain)' deve apontar para $($script:VpsIp)" -ForegroundColor Gray
    Write-Host "    2. Portas 80 e 443 devem estar livres (use opcao 3 se necessario)" -ForegroundColor Gray
    Write-Host "    3. Nginx deve estar rodando na VPS" -ForegroundColor Gray
    Write-Host ""

    if (-not (Test-SshConnection)) {
        Write-Err "Sem conexao SSH. Configure a VPS na opcao 1."
        Read-Host "  Pressione Enter para voltar"
        return
    }

    $confirm = Read-Host "  Confirmar instalacao SSL para '$($script:Domain)'? (S/N)"
    if ($confirm.ToUpper() -ne "S") { return }

    Write-Host ""

    # --- PRE-VERIFICACAO: testar se portas estão livres ---
    Write-Step "Verificando se as portas 80/443 estao disponiveis..."
    $portCheck = Invoke-Ssh -Silent -Cmd @"
PORT80=`$(sudo fuser 80/tcp 2>/dev/null | grep -v nginx || true)
PORT443=`$(sudo fuser 443/tcp 2>/dev/null | grep -v nginx || true)
NGINX_STATUS=`$(systemctl is-active nginx 2>/dev/null || echo 'inactive')
echo "NGINX_STATUS=`$NGINX_STATUS"
if [ -n "`$PORT80" ] || [ -n "`$PORT443" ]; then
    echo "PORTS_BUSY=true"
    sudo ss -tlnp 'sport = :80 or sport = :443' 2>/dev/null | grep -v '^Netid' || true
else
    echo "PORTS_BUSY=false"
fi
"@

    if ($portCheck.Output -match "PORTS_BUSY=true") {
        Write-Err "Portas 80 ou 443 ocupadas por outro processo!"
        Write-Host ""
        Write-Host "  Saida do diagnostico:" -ForegroundColor Yellow
        $portCheck.Output -split "`n" | Where-Object { $_ -notmatch "PORTS_BUSY|NGINX_STATUS" } |
            ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
        Write-Host ""
        Write-Host "  ACAO NECESSARIA:" -ForegroundColor Red
        Write-Host "    Execute a OPCAO 3 (Liberar Portas 80/443) e tente novamente." -ForegroundColor Yellow
        Write-Host ""
        Read-Host "  Pressione Enter para voltar"
        return
    }

    $nginxAtivo = $portCheck.Output -match "NGINX_STATUS=active"
    if (-not $nginxAtivo) {
        Write-Step "Nginx inativo. Tentando iniciar antes do Certbot..."
        $nginxStart = Invoke-Ssh -Silent -Cmd "sudo nginx -t 2>&1 && sudo systemctl start nginx 2>&1 && echo 'NGINX_STARTED'"
        if ($nginxStart.Output -notmatch "NGINX_STARTED") {
            Write-Err "Nao foi possivel iniciar o Nginx."
            Write-Host "  Execute a opcao 3 (Liberar Portas) e tente novamente." -ForegroundColor Yellow
            Read-Host "  Pressione Enter para voltar"
            return
        }
        Write-Ok "Nginx iniciado."
    }

    # --- EXECUTAR INSTALACAO SSL ---
    Write-Step "Instalando certbot e solicitando certificado SSL..."
    $sslCmd = @"
export DOMAIN_NAME='$($script:Domain)'
export EMAIL_ADDRESS='$($script:Email)'
sudo apt-get install -y certbot python3-certbot-nginx -qq 2>&1

# Verificar se já existe certificado válido
if sudo certbot certificates 2>/dev/null | grep -q '$($script:Domain)'; then
    echo 'CERT_EXISTS'
    sudo certbot renew --nginx --cert-name '$($script:Domain)' --non-interactive 2>&1
    echo 'CERT_RENEWED'
else
    # Emitir novo certificado
    sudo certbot --nginx \
        -d '$($script:Domain)' \
        -d 'www.$($script:Domain)' \
        --agree-tos \
        -m '$($script:Email)' \
        --non-interactive \
        --redirect 2>&1
    echo 'CERT_ISSUED'
fi

# Configurar renovação automática
if ! crontab -l 2>/dev/null | grep -q 'certbot renew'; then
    (crontab -l 2>/dev/null; echo '0 3 * * * sudo certbot renew --nginx --quiet && sudo systemctl reload nginx') | crontab -
    echo 'CRON_CONFIGURED'
fi

sudo systemctl reload nginx 2>/dev/null && echo 'NGINX_RELOADED'
"@

    $res = Invoke-Ssh -Cmd $sslCmd

    Write-Host ""
    if ($res.Output -match "CERT_ISSUED|CERT_RENEWED|CERT_EXISTS") {
        Write-Ok "Certificado SSL configurado com sucesso!"
        if ($res.Output -match "CRON_CONFIGURED") {
            Write-Ok "Renovacao automatica configurada (cron diario as 3h)."
        }
        Write-Ok "Acesse: https://$($script:Domain)"
    } elseif ($res.ExitCode -ne 0) {
        Write-Err "Falha na emissao do certificado SSL."
        Write-Host ""
        Write-Host "  Causas comuns:" -ForegroundColor Yellow
        Write-Host "    1. DNS A de '$($script:Domain)' nao aponta para $($script:VpsIp)" -ForegroundColor Gray
        Write-Host "       Verifique: nslookup $($script:Domain)" -ForegroundColor DarkGray
        Write-Host "    2. Porta 80 nao esta acessivel da internet (firewall da Hostinger)" -ForegroundColor Gray
        Write-Host "    3. Rate limit do Let's Encrypt (max 5 emissoes/semana por dominio)" -ForegroundColor Gray
        Write-Host "       Teste com: sudo certbot --nginx -d $($script:Domain) --dry-run" -ForegroundColor DarkGray
        Write-Host ""
        Write-Host "  Use a opcao 12 (SSH interativo) para diagnosticar manualmente." -ForegroundColor Yellow
    }

    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 5 — Status SSL
function Get-SslStatus {
    Write-Banner
    Write-Step "Verificando certificados SSL na VPS..."
    Write-Host ""
    $res = Invoke-Ssh -Cmd "sudo certbot certificates 2>&1 || echo 'CERTBOT_NAO_INSTALADO'"
    Write-Host ""
    if ($res.Output -match "CERTBOT_NAO_INSTALADO|not found|command not found") {
        Write-Host "  Certbot nao esta instalado. Use a opcao 4 para instalar SSL." -ForegroundColor Yellow
    } elseif ($res.Output -match "No certificates found") {
        Write-Host "  Certbot instalado mas sem certificados. Use a opcao 4." -ForegroundColor Yellow
    } else {
        Write-Ok "Certificados encontrados (veja acima)."
    }
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 6 — Status Docker
function Get-DockerStatus {
    Write-Banner
    Write-Step "Status dos containers Docker na VPS..."
    Write-Host ""
    Invoke-Ssh -Cmd @"
cd /var/www/portalcursos 2>/dev/null && \
docker compose -f devops/docker-compose.prod.yml ps 2>&1 || \
docker ps -a 2>&1
"@
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 7 — Logs Backend
function Get-BackendLogs {
    Write-Banner
    Write-Step "Ultimas 100 linhas do Backend (Spring Boot)..."
    Write-Host ""
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml logs --tail=100 backend 2>&1"
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 8 — Logs Frontend
function Get-FrontendLogs {
    Write-Banner
    Write-Step "Ultimas 100 linhas do Frontend (Next.js)..."
    Write-Host ""
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml logs --tail=100 frontend 2>&1"
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 9 — Reiniciar Nginx
function Restart-Nginx {
    Write-Banner
    Write-Step "Testando e reiniciando Nginx na VPS..."
    Write-Host ""
    $res = Invoke-Ssh -Cmd "sudo nginx -t 2>&1 && sudo systemctl reload nginx && echo 'NGINX_OK' || echo 'NGINX_FAIL'"
    Write-Host ""
    if ($res.Output -match "NGINX_OK") {
        Write-Ok "Nginx recarregado com sucesso."
    } else {
        Write-Err "Falha ao recarregar o Nginx."
        Write-Host "  Verifique: sudo nginx -t" -ForegroundColor Yellow
    }
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 10 — Health Check Completo
function Invoke-HealthCheck {
    Write-Banner
    Write-Host "  HEALTH CHECK COMPLETO DA VPS" -ForegroundColor Yellow
    Write-Host ""

    $checks = @(
        @{ Label = "Conexao SSH"      ; Cmd = "echo 'OK'" },
        @{ Label = "Nginx ativo"      ; Cmd = "systemctl is-active nginx 2>&1" },
        @{ Label = "Docker ativo"     ; Cmd = "systemctl is-active docker 2>&1" },
        @{ Label = "Container backend"; Cmd = "docker inspect --format='{{.State.Status}}' portalcursos_backend 2>/dev/null || echo 'nao encontrado'" },
        @{ Label = "Container frontend";Cmd = "docker inspect --format='{{.State.Status}}' portalcursos_frontend 2>/dev/null || echo 'nao encontrado'" },
        @{ Label = "API /health"      ; Cmd = "curl -sf --max-time 10 http://localhost:8090/api/health 2>/dev/null | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get(\"status\",\"?\"))' 2>/dev/null || echo 'sem resposta'" },
        @{ Label = "Frontend HTTP"    ; Cmd = "curl -sf --max-time 10 -o /dev/null -w '%{http_code}' http://localhost:3010 2>/dev/null || echo 'sem resposta'" },
        @{ Label = "HTTPS externo"    ; Cmd = "curl -sf --max-time 15 -o /dev/null -w '%{http_code}' https://$($script:Domain) 2>/dev/null || echo 'sem resposta'" },
        @{ Label = "Cert SSL validade"; Cmd = "sudo certbot certificates 2>/dev/null | grep -E 'Expiry|VALID|INVALID' | head -2 || echo 'sem certificado'" },
        @{ Label = "Espaco em disco"  ; Cmd = "df -h / | awk 'NR==2{print `$5 \" usado de \" `$2}'" },
        @{ Label = "Memoria RAM"      ; Cmd = "free -h | awk '/^Mem:/{print `$3 \" de \" `$2 \" em uso\"}'" },
        @{ Label = "Porta 443 livre"  ; Cmd = "sudo ss -tlnp 'sport = :443' 2>/dev/null | grep -q LISTEN && echo 'em uso (OK se Nginx)' || echo 'LIVRE (Nginx nao escuta)'" }
    )

    $ok = 0; $fail = 0
    foreach ($c in $checks) {
        $res = Invoke-Ssh -Cmd $c.Cmd -Silent
        $out = $res.Output.Trim() -replace "`n", " "
        $label = "  {0,-22}" -f $c.Label

        $isOk = $res.ExitCode -eq 0 -and
                $out -notmatch "sem resposta|nao encontrado|INVALID|inactive|LIVRE \(Nginx" -and
                $out -notmatch "^0$"

        if ($isOk) {
            Write-Host "$label" -NoNewline -ForegroundColor White
            Write-Host " [OK] $out" -ForegroundColor Green
            $ok++
        } else {
            Write-Host "$label" -NoNewline -ForegroundColor White
            Write-Host " [!!] $out" -ForegroundColor Red
            $fail++
        }
    }

    Write-Host ""
    $total = $ok + $fail
    Write-Host ("  Resultado: {0}/{1} checks OK" -f $ok, $total) -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Yellow" })

    if ($fail -gt 0) {
        Write-Host ""
        Write-Host "  Dicas para problemas comuns:" -ForegroundColor Yellow
        Write-Host "    Porta 443 nao escuta  → opcao 3 (Liberar Portas) + opcao 4 (SSL)" -ForegroundColor Gray
        Write-Host "    Container parado      → opcao 11 (Reiniciar Containers)" -ForegroundColor Gray
        Write-Host "    API sem resposta      → opcao 7 (Logs Backend)" -ForegroundColor Gray
        Write-Host "    HTTPS sem resposta    → opcao 9 (Reiniciar Nginx)" -ForegroundColor Gray
    }

    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 11 — Reiniciar Containers
function Restart-AllContainers {
    Write-Banner
    Write-Host "  REINICIAR TODOS OS CONTAINERS DOCKER" -ForegroundColor Yellow
    Write-Host "  Isso causara breve indisponibilidade (30-60s)." -ForegroundColor Gray
    Write-Host ""
    $confirm = Read-Host "  Confirmar reinicio? (S/N)"
    if ($confirm.ToUpper() -ne "S") { return }

    Write-Host ""
    Invoke-Ssh -Cmd "cd /var/www/portalcursos && docker compose -f devops/docker-compose.prod.yml restart 2>&1"
    Write-Host ""
    Write-Ok "Containers reiniciados. Aguarde 30s para o backend inicializar."
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# OPÇÃO 12 — SSH interativo
function Open-SshShell {
    Write-Banner
    Write-Host "  SESSAO SSH INTERATIVA NA VPS" -ForegroundColor Magenta
    Write-Host "  Voce tera acesso direto ao terminal Linux da VPS." -ForegroundColor Gray
    Write-Host "  Digite 'exit' para fechar a sessao e voltar ao menu." -ForegroundColor DarkGray
    Write-Host ""
    Read-Host "  Pressione Enter para conectar"
    Write-Host ""

    $sshArgs = @()
    if ($PrivateKeyPath -and (Test-Path $PrivateKeyPath)) { $sshArgs += "-i", $PrivateKeyPath }
    $sshArgs += "-o", "StrictHostKeyChecking=no"
    $sshArgs += "$VpsUser@$($script:VpsIp)"
    & ssh $sshArgs
    Write-Host ""
    Write-Host "  Sessao SSH encerrada." -ForegroundColor DarkGray
    Start-Sleep 1
}

# OPÇÃO 13 — Renovar SSL
function Renew-Ssl {
    Write-Banner
    Write-Step "Renovando certificados SSL via Certbot na VPS..."
    Write-Host ""
    $res = Invoke-Ssh -Cmd "sudo certbot renew --nginx 2>&1 && sudo systemctl reload nginx && echo 'RENEW_OK'"
    Write-Host ""
    if ($res.Output -match "RENEW_OK") {
        Write-Ok "Certificado SSL renovado com sucesso."
    } else {
        Write-Host "  Veja a saida acima para detalhes." -ForegroundColor Yellow
    }
    Write-Host ""
    Read-Host "  Pressione Enter para voltar"
}

# ---------------------------------------------------------------
# LOOP PRINCIPAL DO MENU
# ---------------------------------------------------------------
while ($true) {
    Write-Banner

    Write-Host "  --- CONFIGURACAO ---" -ForegroundColor DarkCyan
    Write-Host "   1.  Definir IP / Dominio da VPS" -ForegroundColor White
    Write-Host "   2.  Testar conexao SSH" -ForegroundColor White
    Write-Host ""
    Write-Host "  --- PORTAS E SSL ---" -ForegroundColor DarkCyan
    Write-Host "   3.  Liberar portas 80/443 (resolver conflito 'bind() failed')" -ForegroundColor Yellow
    Write-Host "   4.  Instalar / Emitir SSL (Let's Encrypt)" -ForegroundColor Green
    Write-Host "   5.  Verificar certificados SSL" -ForegroundColor White
    Write-Host "  13.  Renovar certificado SSL" -ForegroundColor White
    Write-Host ""
    Write-Host "  --- MONITORAMENTO ---" -ForegroundColor DarkCyan
    Write-Host "   6.  Status dos containers Docker" -ForegroundColor White
    Write-Host "   7.  Logs do Backend (Spring Boot)" -ForegroundColor White
    Write-Host "   8.  Logs do Frontend (Next.js)" -ForegroundColor White
    Write-Host "  10.  Health Check completo da VPS" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  --- OPERACOES ---" -ForegroundColor DarkCyan
    Write-Host "   9.  Reiniciar Nginx" -ForegroundColor White
    Write-Host "  11.  Reiniciar todos os containers" -ForegroundColor White
    Write-Host "  12.  Abrir sessao SSH interativa (terminal Linux)" -ForegroundColor Magenta
    Write-Host ""
    Write-Host "   0.  Sair" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Cyan

    $choice = Read-Host "  Selecione uma opcao"
    Write-Host ""

    try {
        switch ($choice.Trim()) {
            "1"  { Set-VpsTarget }
            "2"  { Test-SshNow }
            "3"  { Invoke-FixPorts }
            "4"  { Install-Ssl }
            "5"  { Get-SslStatus }
            "6"  { Get-DockerStatus }
            "7"  { Get-BackendLogs }
            "8"  { Get-FrontendLogs }
            "9"  { Restart-Nginx }
            "10" { Invoke-HealthCheck }
            "11" { Restart-AllContainers }
            "12" { Open-SshShell }
            "13" { Renew-Ssl }
            "0"  { Write-Host "  Encerrando. Ate breve!" -ForegroundColor Cyan; exit 0 }
            default {
                Write-Host "  Opcao invalida. Escolha entre 0-13." -ForegroundColor Yellow
                Start-Sleep 1
            }
        }
    } catch {
        Write-Host ""
        Write-Err $_.Exception.Message
        Write-Host ""
        Read-Host "  Pressione Enter para voltar"
    }
}
