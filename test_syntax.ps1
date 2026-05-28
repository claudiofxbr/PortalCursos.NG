$errs = $null
$tokens = $null
[System.Management.Automation.Language.Parser]::ParseFile('C:\Users\VeKTI-01\Desktop\Unifacs\Engenharia\Aplicativos\PortalCursos.NG\deploy-hostinger.ps1', [ref]$tokens, [ref]$errs) | Out-Null
if ($errs) {
    foreach ($e in $errs) {
        Write-Host "Linha $($e.Extent.StartLineNumber): $($e.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "Sem erros no deploy-hostinger.ps1" -ForegroundColor Green
}
