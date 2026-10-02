<#
 Envia o consumo de tokens do Claude Code (deste computador) para o app Controle de Tokens na VPS.
 Faz tudo em sequencia e PARA no primeiro erro:
   1) confere o Node 20+   2) confere o collector   3) conta as mensagens (sem enviar)
   4) pede a chave (oculta) e valida o formato   5) pede confirmacao   6) envia   7) limpa a chave da memoria

 Uso:   powershell -ExecutionPolicy Bypass -File $HOME\enviar-consumo.ps1
        ... -Days 35        (historico a enviar; padrao 35 = mes inteiro)
        ... -Watch          (modo continuo: reenvia a cada 5 min ate Ctrl+C)
        ... -Dir "C:\caminho\projects"   (se o Claude Code guarda os dados em outro lugar)
        ... -Update         (baixa a versao mais nova do collector do GitHub antes de rodar)
        ... -FetchKeyViaSsh (busca a chave na VPS por SSH, sem voce copiar/colar; a chave nunca e exibida)
 Observacao: este arquivo e propositalmente ASCII (sem acentos) para o PowerShell 5 nao corromper o texto.
#>
param(
    [string]$Url = "https://xavierbr-vps.tech/tokencontrol",
    [int]$Days = 35,
    [switch]$Watch,
    [switch]$SkipConfirm,
    [switch]$Update,
    [switch]$FetchKeyViaSsh,
    [string]$SshTarget = "root@69.62.87.38",
    [string]$EnvFileOnVps = "/var/www/token-control/.env",
    [string]$Dir = "",
    [string]$Collector = (Join-Path $HOME "collect.mjs")
)
$ErrorActionPreference = "Stop"
function Fail([string]$msg) { Write-Host "ERRO: $msg" -ForegroundColor Red; exit 1 }

# 0) Atualizar o collector a partir do GitHub (opcional)
if ($Update) {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail "git nao encontrado (necessario para -Update)." }
    $src = Join-Path $HOME "tc-src"
    if (Test-Path (Join-Path $src ".git")) { & git -C $src pull --ff-only; if ($LASTEXITCODE -ne 0) { Fail "git pull falhou em $src" } }
    else { & git clone --depth 1 --branch claude/bold-mayer-tdvqlf --single-branch https://github.com/claudiofxbr/PortalCursos.NG.git $src; if ($LASTEXITCODE -ne 0) { Fail "git clone falhou" } }
    Copy-Item (Join-Path $src "token-control\collector\collect.mjs") $Collector -Force
    Write-Host "[0/6] Collector atualizado a partir do GitHub"
}

# 1) Node
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Fail "Node.js nao encontrado. Instale o Node 20 ou mais novo (https://nodejs.org) e rode de novo." }
$nodeMajor = [int]((& node --version).TrimStart("v").Split(".")[0])
if ($nodeMajor -lt 20) { Fail "Node $nodeMajor e muito antigo; precisa do Node 20 ou mais novo." }
Write-Host "[1/6] Node $nodeMajor ok"

# 2) Collector
if (-not (Test-Path $Collector)) { Fail "collect.mjs nao encontrado em '$Collector'. Rode antes o passo de download do collector." }
Write-Host "[2/6] Collector encontrado: $Collector"

# 3) Contagem (nada e enviado)
$extra = @(); if ($Dir) { $extra = @("--dir", $Dir) }
Write-Host "[3/6] Contando mensagens dos ultimos $Days dias (nada e enviado)..."
$dry = & node $Collector --dry-run --days $Days @extra 2>&1
if ($LASTEXITCODE -ne 0) { Fail "nao foi possivel ler os dados do Claude Code: $dry" }
$dryText = ($dry | Out-String)
Write-Host $dryText.Trim()
if ($dryText -notmatch "(\d+) mensagens") { Fail "resposta inesperada do collector: $dryText" }
if ([int]$Matches[1] -eq 0) { Fail "nenhuma mensagem encontrada. O Claude Code guarda os dados em outra pasta? Use -Dir 'CAMINHO'." }

# 4) Chave: por SSH na VPS (-FetchKeyViaSsh, nada e exibido) ou colada (oculta). Validacao de formato: 64 hexadecimais.
if ($FetchKeyViaSsh) {
    if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) { Fail "ssh nao encontrado (Windows 10/11: Configuracoes > Aplicativos > Recursos opcionais > Cliente OpenSSH)." }
    Write-Host "[4/6] Buscando a chave em ${SshTarget}:$EnvFileOnVps via SSH (a chave nao sera exibida)..."
    $remote = "grep -m1 '^TOKEN_CONTROL_API_KEY=' $EnvFileOnVps | cut -d= -f2-"
    $raw = & ssh $SshTarget $remote
    if ($LASTEXITCODE -ne 0) { Fail "ssh falhou (acesso a $SshTarget). Rode sem -FetchKeyViaSsh e cole a chave." }
    $key = ($raw | Out-String).Trim()
} else {
    $secure = Read-Host "[4/6] Cole a chave do collector (nada aparece ao colar) e tecle Enter" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim() }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
if ($key.Length -ne 64 -or $key -notmatch "^[0-9a-fA-F]+$") {
    $key = $null
    Fail "chave invalida: deve ter exatamente 64 caracteres (0-9 e a-f). Confira se nao colou espaco ou linha a mais."
}

# 4b) Mostra so o inicio e o fim da chave (para comparar com a da VPS) e TESTA a chave antes de enviar:
#     POST com lote vazio -> 400 = chave aceita (lote vazio e invalido, mas a chave passou) ; 401 = chave recusada.
Write-Host ("      Chave recebida: {0}...{1}  (compare com a da VPS: inicio e fim devem ser iguais)" -f $key.Substring(0, 4), $key.Substring(60))
function Get-KeyStatus([string]$baseUrl, [string]$apiKey) {
    try {
        $r = Invoke-WebRequest -Uri "$baseUrl/api/tokens/usage" -Method Post -Headers @{ "X-API-Key" = $apiKey } `
            -ContentType "application/json" -Body '{"entries":[]}' -UseBasicParsing
        return [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}
$status = Get-KeyStatus $Url $key
if ($status -eq 401) { $key = $null; Fail "o app RECUSOU esta chave (401). Provavelmente e a chave antiga ou uma copia errada. Pegue de novo na VPS (comeca com 21db e termina com c6e4)." }
if ($status -eq 0)   { $key = $null; Fail "nao consegui falar com $Url (rede/endereco). Abra a URL no navegador para conferir." }
if ($status -ne 400 -and $status -ne 200) { $key = $null; Fail "resposta inesperada do app no teste da chave: HTTP $status." }
Write-Host "      Chave aceita pelo app (teste HTTP $status)." -ForegroundColor Green

# 5) Confirmacao
if (-not $SkipConfirm) {
    $r = Read-Host "[5/6] Enviar o consumo para $Url ? (s/N)"
    if ($r -notmatch "^[sSyY]") { $key = $null; Write-Host "Cancelado. Nada foi enviado."; exit 0 }
}

# 6) Envio
$env:TOKEN_CONTROL_URL = $Url
$env:TOKEN_CONTROL_API_KEY = $key
try {
    if ($Watch) {
        Write-Host "[6/6] Modo continuo: reenvia a cada 5 min. Ctrl+C para parar."
        & node $Collector --days 2 --watch 300 @extra
    } else {
        Write-Host "[6/6] Enviando..."
        & node $Collector --days $Days @extra
        if ($LASTEXITCODE -ne 0) { Fail "o envio falhou (veja a mensagem acima; 'Backend respondeu 401' = chave errada ou antiga)." }
    }
} finally {
    Remove-Item Env:TOKEN_CONTROL_API_KEY -ErrorAction SilentlyContinue   # a chave nao fica na sessao
    $key = $null
}
Write-Host "Pronto. Abra $Url e veja a aba 'Mes atual'." -ForegroundColor Green
