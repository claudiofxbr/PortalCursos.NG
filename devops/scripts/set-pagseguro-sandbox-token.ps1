# Grava as credenciais de SANDBOX do PagBank/PagSeguro em backend/.env (arquivo local,
# ja ignorado pelo Git, nunca versionado). Entrada mascarada: o valor nunca aparece na
# tela nem fica no historico do PowerShell - so fica gravado no arquivo local.
#
# Uso: rode este script na raiz do projeto (mesma pasta deste repo).
#   powershell -ExecutionPolicy Bypass -File devops\scripts\set-pagseguro-sandbox-token.ps1

$ErrorActionPreference = "Stop"

$envFile = Join-Path (Join-Path $PSScriptRoot "..\..") "backend\.env"
$envFile = [System.IO.Path]::GetFullPath($envFile)

if (-not (Test-Path $envFile)) {
    Write-Host "[ERRO] Arquivo nao encontrado: $envFile" -ForegroundColor Red
    Write-Host "Rode este script a partir da raiz do repo, ou crie backend\.env primeiro (copie de backend\.env.example)." -ForegroundColor Red
    exit 1
}

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Configurar credenciais de SANDBOX do PagBank/PagSeguro    " -ForegroundColor Cyan
Write-Host "  (gravado so em backend\.env local, nunca commitado)       " -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

function Read-SecretAsPlainText {
    param([string]$Prompt)
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$token = Read-SecretAsPlainText -Prompt "Cole o PAGSEGURO_API_TOKEN de sandbox (nao aparece na tela)"
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "[ERRO] Token vazio. Nada foi alterado." -ForegroundColor Red
    exit 1
}

$webhookSecret = Read-SecretAsPlainText -Prompt "Cole o PAGSEGURO_WEBHOOK_SECRET (ou so ENTER para gerar um automatico)"
if ([string]::IsNullOrWhiteSpace($webhookSecret)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    $webhookSecret = [Convert]::ToBase64String($bytes)
    Write-Host "    -> PAGSEGURO_WEBHOOK_SECRET gerado automaticamente." -ForegroundColor DarkCyan
}

# Base URL de sandbox do PagBank (ambiente de testes, nao processa pagamento real).
$baseUrl = "https://sandbox.api.pagseguro.com"

function Set-EnvVar {
    param([string]$Path, [string]$Key, [string]$Value)
    $lines = Get-Content -Path $Path
    $pattern = "^" + [regex]::Escape($Key) + "="
    $found = $false
    $newLines = @()
    foreach ($line in $lines) {
        if ($line -match $pattern) {
            $found = $true
            $newLines += "$Key=$Value"
        } else {
            $newLines += $line
        }
    }
    if (-not $found) {
        $newLines += "$Key=$Value"
    }
    Set-Content -Path $Path -Value $newLines -Encoding UTF8
}

Set-EnvVar -Path $envFile -Key "PAGSEGURO_API_TOKEN" -Value $token
Set-EnvVar -Path $envFile -Key "PAGSEGURO_API_BASE_URL" -Value $baseUrl
Set-EnvVar -Path $envFile -Key "PAGSEGURO_WEBHOOK_SECRET" -Value $webhookSecret
Set-EnvVar -Path $envFile -Key "PORTALCURSOS_PUBLIC_API_URL" -Value "http://localhost:8080"

# Limpa as variaveis da memoria assim que possivel.
$token = $null
$webhookSecret = $null
[System.GC]::Collect()

Write-Host ""
Write-Host "[OK] Credenciais gravadas em backend\.env" -ForegroundColor Green
Write-Host "     PAGSEGURO_API_BASE_URL = $baseUrl" -ForegroundColor Green
Write-Host "     Nenhum valor foi exibido na tela ou enviado para fora desta maquina." -ForegroundColor Green
Write-Host "     Avise no chat que terminou." -ForegroundColor Green
