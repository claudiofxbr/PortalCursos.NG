# PortalCursos.NG: Utilitário de Publicação GitHub V30.0-SUPREME
# Este script automatiza e protege o envio de atualizações para o GitHub.

Clear-Host
echo "==========================================================="
echo "   PORTAL CURSOS NG - PUBLICADOR GITHUB V30.0-SUPREME     "
echo "==========================================================="

# 1. Verificação de Repositório Git
if (-not (Test-Path ".git")) {
    echo "[!] Repositório Git não encontrado. Inicializando..."
    git init
}

# 2. Verificação de Remoto (Origin)
$remote = git remote -v
if (-not $remote) {
    echo "[!] URL do GitHub não configurada."
    $repoUrl = Read-Host "Por favor, cole a URL do seu repositório GitHub"
    if ($repoUrl) {
        git remote add origin $repoUrl
        echo "[V30.0] Sucesso: Remoto 'origin' configurado."
    } else {
        echo "Operação cancelada."
        exit
    }
}

# 3. Sincronização Prévia (Pull)
echo "[V30.0] Sincronizando com o repositório remoto (Pull)..."
git pull origin main --rebase

# 4. Preparação dos Arquivos
echo "[V30.0] Analisando alterações..."
$status = git status --porcelain
if (-not $status) {
    echo "[V30.0] Nenhuma alteração detectada. Sistema está sincronizado."
    exit
}

echo "[V30.0] Indexando alterações..."
git add .

# 5. Mensagem de Commit
$msg = Read-Host "Digite a mensagem da atualização (Enter para 'Protocolo V30.0-SUPREME')"
if (-not $msg) { $msg = "Protocolo V30.0-SUPREME - Estabilização de Infraestrutura" }

echo "[V30.0] Registrando commit: '$msg'..."
git commit -m "[V30.0] $msg"

# 6. Envio (Push)
echo "[V30.0] Enviando para o GitHub (branch main)..."
git branch -M main
git push -u origin main

echo "==========================================================="
echo "   PROCESSO CONCLUÍDO COM SUCESSO!                        "
echo "   Seu código está agora seguro no GitHub.               "
echo "==========================================================="
