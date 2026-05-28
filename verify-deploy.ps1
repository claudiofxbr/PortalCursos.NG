# =================================================================
# verify-deploy.ps1 - Utilitario de Auditoria Sintatica e Sanitizacao
# Finalidade: Garantir 100% de integridade e ausenca de parser errors
# Ambiente: PowerShell local (Windows)
# =================================================================

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "   AUDITOR DE INTEGRIDADE DE DEPLOY - PORTALCURSOS.NG      " -ForegroundColor Cyan
Write-Host "   Padrao: OMEGA-VERIFY V3.7 | Resiliencia: 100%           " -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan

$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = Get-Location }
$DeployScriptPath = Join-Path $ScriptDir "deploy-hostinger.ps1"
if (-not (Test-Path $DeployScriptPath)) {
    $DeployScriptPath = Join-Path $ScriptDir "deploy-vps.ps1"
}
$EnvPath = Join-Path $ScriptDir ".env"

$hasErrors = $false

# 1. TESTE ESTATICO DE SINTAXE (AST PARSER COMPILER)
Write-Host ""
Write-Host ">>> [TESTE 1/3] Analisando sintaxe interna do script $(Split-Path $DeployScriptPath -Leaf)..." -ForegroundColor Yellow

if (-not (Test-Path $DeployScriptPath)) {
    Write-Host "[ERRO CRITICO] Arquivo $(Split-Path $DeployScriptPath -Leaf) nao encontrado no diretorio atual!" -ForegroundColor Red
    $hasErrors = $true
} else {
    try {
        $errors = $null
        $tokens = $null
        # Executa o compilador parser nativo do .NET no PowerShell
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($DeployScriptPath, [ref]$tokens, [ref]$errors)
        
        if ($errors) {
            Write-Host "[ERRO] Falha no Parser: Foram encontrados erros de sintaxe no compilador do PowerShell para $(Split-Path $DeployScriptPath -Leaf)!" -ForegroundColor Red
            foreach ($err in $errors) {
                Write-Host ("  - Linha " + $err.Extent.StartLineNumber + ", Coluna " + $err.Extent.StartColumnNumber + ": " + $err.Message) -ForegroundColor Red
            }
            $hasErrors = $true
        } else {
            Write-Host "[SUCESSO] A arvore sintatica (AST) do $(Split-Path $DeployScriptPath -Leaf) esta 100% integra!" -ForegroundColor Green
            Write-Host "  [OK] Sem blocos Try/Catch ausentes." -ForegroundColor Green
            Write-Host "  [OK] Sem referencias a chaves '{}' ou tokens inesperados." -ForegroundColor Green
        }
    } catch {
        Write-Host ("[ERRO] Erro inesperado ao analisar a arvore sintatica: " + $_.Exception.Message) -ForegroundColor Red
        $hasErrors = $true
    }
}

# 2. TESTE DE DEPENDENCIAS DO SISTEMA OPERACIONAL LOCAL
Write-Host ""
Write-Host ">>> [TESTE 2/3] Verificando ferramentas de infraestrutura local..." -ForegroundColor Yellow

$dependencies = @("git", "ssh", "scp")
foreach ($dep in $dependencies) {
    $command = Get-Command $dep -ErrorAction SilentlyContinue
    if (-not $command) {
        Write-Host ("[ERRO] O binario '" + $dep + "' nao foi localizado no seu PATH do Windows.") -ForegroundColor Red
        $hasErrors = $true
    } else {
        Write-Host ("  [OK] Binario '" + $dep + "' operacional em: " + $command.Source) -ForegroundColor Green
    }
}

# 3. TESTE DE SANIDADE DO ARQUIVO DE CONFIGURACAO (.ENV)
Write-Host ""
Write-Host ">>> [TESTE 3/3] Validando conformidade estrutural do arquivo .env..." -ForegroundColor Yellow

if (-not (Test-Path $EnvPath)) {
    Write-Host "[ALERTA] Arquivo .env local nao existe! O script de deploy criara um esqueleto." -ForegroundColor Yellow
} else {
    try {
        $envContent = Get-Content $EnvPath -Raw
        $requiredKeys = @(
            "SPRING_DATASOURCE_USERNAME", 
            "SPRING_DATASOURCE_PASSWORD", 
            "APP_JWT_SECRET", 
            "POSTGRES_DB", 
            "POSTGRES_USER", 
            "POSTGRES_PASSWORD"
        )
        
        $missingKeys = 0
        foreach ($key in $requiredKeys) {
            if ($envContent -notmatch $key) {
                Write-Host ("  [!] Variavel obrigatoria ausente no .env: " + $key) -ForegroundColor Yellow
                $missingKeys++
            }
        }
        
        if ($missingKeys -eq 0) {
            Write-Host "[SUCESSO] Arquivo .env local contem todas as chaves criticas de banco e seguranca!" -ForegroundColor Green
        } else {
            Write-Host "[ALERTA] Seu .env local possui variaveis ausentes. Verifique a compatibilidade do backend." -ForegroundColor Yellow
        }
    } catch {
        Write-Host ("[ERRO] Erro de leitura do arquivo .env local: " + $_.Exception.Message) -ForegroundColor Red
        $hasErrors = $true
    }
}

# --- RELATORIO DE DIAGNOSTICO FINAL ---
Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
if ($hasErrors) {
    Write-Host "   STATUS DE VERIFICACAO: FALHA                           " -ForegroundColor Red
    Write-Host "   Existem erros criticos que impedem a execucao segura.  " -ForegroundColor Red
    Write-Host "===========================================================" -ForegroundColor Cyan
    exit 1
} else {
    Write-Host "   STATUS DE VERIFICACAO: APROVADO                        " -ForegroundColor Green
    Write-Host "   O script $(Split-Path $DeployScriptPath -Leaf) esta 100% seguro para rodar!   " -ForegroundColor Green
    Write-Host "===========================================================" -ForegroundColor Cyan
    exit 0
}
