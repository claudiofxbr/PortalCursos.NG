# PortalCursos.NG - Script de Atualizacao de Seguranca GitHub V31.3
# Este script realiza a higienizacao de chaves SSH vazadas e gera novas chaves Ed25519 seguras.

$ErrorActionPreference = "Stop"
Clear-Host

Write-Host "PORTAL CURSOS NG - ATUALIZADOR DE SEGURANCA GITHUB" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------" -ForegroundColor Cyan

# 1. Verificacao do Repositorio Git
if (-not (Test-Path ".git")) {
    Write-Host "[!] Diretorio Git nao encontrado na raiz. Execute na raiz do projeto." -ForegroundColor Red
    Exit
}

# 2. Higienizacao: Remocao de Chaves SSH Expostas na Raiz
Write-Host "[*] Iniciando higienizacao do diretorio do projeto..." -ForegroundColor Yellow
$chavesComprometidas = @(
    "final_key", "final_key.pub",
    "git_key", "git_key.pub",
    "new_id_rsa", "new_id_rsa.pub",
    "temp_key", "temp_key.pub",
    "test_key", "test_key.pub",
    "test_ed25519", "test_ed25519.pub"
)

$removidos = 0
foreach ($chave in $chavesComprometidas) {
    if (Test-Path $chave) {
        Write-Host "    -> Removendo chave exposta: $chave" -ForegroundColor DarkYellow
        Remove-Item -Path $chave -Force
        $removidos++
    }
}

if ($removidos -gt 0) {
    Write-Host "[+] Limpeza concluida: $removidos arquivos removidos da raiz." -ForegroundColor Green
} else {
    Write-Host "[+] Nenhuma chave privada exposta encontrada na raiz do projeto." -ForegroundColor Green
}
Write-Host ""

# 3. Validacao do .gitignore para protecao do arquivo .env e chaves
Write-Host "[*] Validando regras de protecao no .gitignore..." -ForegroundColor Yellow
if (Test-Path ".gitignore") {
    $conteudoGitignore = Get-Content ".gitignore"
    
    # Garantir que .env esta ignorado
    if ($conteudoGitignore -notcontains ".env") {
        Write-Host "    -> Adicionando .env ao .gitignore..." -ForegroundColor DarkYellow
        Add-Content -Path ".gitignore" -Value "`n# Variaveis de Ambiente (Seguranca)`n.env`n.env.local"
    }

    # Garantir regras de protecao de chaves
    $regrasChaves = @("*_key*", "*id_rsa*", "*ed25519*")
    foreach ($regra in $regrasChaves) {
        if ($conteudoGitignore -notcontains $regra) {
            Write-Host "    -> Adicionando regra de protecao ao .gitignore..." -ForegroundColor DarkYellow
            Add-Content -Path ".gitignore" -Value $regra
        }
    }
    Write-Host "[+] Regras de seguranca validadas no .gitignore." -ForegroundColor Green
} else {
    Write-Host "[!] .gitignore nao encontrado! Criando padrao seguro..." -ForegroundColor Red
    $novoGitignore = "# Dependencias`r`nnode_modules/`r`nbackend/target/`r`nfrontend/.next/`r`n`r`n# Temporarios`r`n*.log`r`n.DS_Store`r`n`r`n# Variaveis de Ambiente (Seguranca)`r`n.env`r`n.env.local`r`n`r`n# Protecao de Chaves SSH`r`n*_key*`r`n*id_rsa*`r`n*ed25519*"
    Set-Content -Path ".gitignore" -Value $novoGitignore
    Write-Host "[+] .gitignore de seguranca criado com sucesso." -ForegroundColor Green
}
Write-Host ""

# 4. Geracao de Nova Chave SSH com Algoritmo Forte (Ed25519)
Write-Host "[*] Configuracao de nova chave segura Ed25519..." -ForegroundColor Yellow
$homeSshDir = Join-Path $env:USERPROFILE ".ssh"

if (-not (Test-Path $homeSshDir)) {
    Write-Host "    -> Criando diretorio seguro de SSH..." -ForegroundColor DarkYellow
    New-Item -Path $homeSshDir -ItemType Directory | Out-Null
}

$caminhoNovaChave = Join-Path $homeSshDir "id_ed25519_portalcursos"
$caminhoNovaChavePub = "$caminhoNovaChave.pub"

$gerarChave = Read-Host "Deseja gerar nova chave Ed25519 para conexao com GitHub? (S/N)"
if ($gerarChave -eq "S" -or $gerarChave -eq "s") {
    if (Test-Path $caminhoNovaChave) {
        $sobrescrever = Read-Host "Ja existe uma chave. Sobrescrever? (S/N)"
        if ($sobrescrever -ne "S" -and $sobrescrever -ne "s") {
            Write-Host "[+] Mantendo a chave existente." -ForegroundColor Green
            $gerarChave = "N"
        }
    }

    if ($gerarChave -eq "S" -or $gerarChave -eq "s") {
        Write-Host "    -> Gerando nova chave SSH Ed25519..." -ForegroundColor DarkYellow
        & ssh-keygen -t ed25519 -C portalcursos-ng-deploy -f $caminhoNovaChave -N ""
        Write-Host "[+] Nova chave gerada com sucesso!" -ForegroundColor Green
    }
}
Write-Host ""

# 5. Exibicao da Chave Publica para Configuracao no GitHub
if (Test-Path $caminhoNovaChavePub) {
    $pubKey = Get-Content $caminhoNovaChavePub
    Write-Host ""
    Write-Host "-----------------------------------------------------------" -ForegroundColor Green
    Write-Host "🔒 CHAVE PUBLICA GERADA (Adicione ao GitHub):" -ForegroundColor Green
    Write-Host "-----------------------------------------------------------" -ForegroundColor Green
    Write-Host $pubKey -ForegroundColor White
    Write-Host "-----------------------------------------------------------" -ForegroundColor Green
    Write-Host "Como configurar no GitHub:" -ForegroundColor Yellow
    Write-Host "1. Copie a linha acima." -ForegroundColor Yellow
    Write-Host "2. Acesse seu repositorio no GitHub." -ForegroundColor Yellow
    Write-Host "3. Va em Settings -> Deploy Keys -> Add deploy key." -ForegroundColor Yellow
    Write-Host "4. Insira um titulo e cole a chave." -ForegroundColor Yellow
    Write-Host "5. Deixe a opcao Allow Write Access desmarcada." -ForegroundColor Yellow
    Write-Host "-----------------------------------------------------------" -ForegroundColor Green
}
Write-Host ""

# 6. Alerta de Historico do Git
Write-Host "WARNING - SEGURANCA DO HISTORICO GIT:" -ForegroundColor Orange
Write-Host "Se voce ja efetuou commits com chaves privadas no passado, elas permanecem" -ForegroundColor Orange
Write-Host "visiveis no historico do repositorio remoto. Para limpar completamente," -ForegroundColor Orange
Write-Host "considere utilizar a ferramenta BFG Repo-Cleaner com o comando:" -ForegroundColor White
Write-Host "   java -jar bfg.jar --delete-files *.key*" -ForegroundColor White
Write-Host ""

Write-Host "-----------------------------------------------------------" -ForegroundColor Cyan
Write-Host "   ATUALIZACAO DE SEGURANCA LOCAL CONCLUIDA COM SUCESSO!" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------" -ForegroundColor Cyan
