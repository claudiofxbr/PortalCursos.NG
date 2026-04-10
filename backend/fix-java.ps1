# Script para Corrigir o Java (JAVA_HOME) e Maven (M2_HOME) no Windows
# Versao Limpa (Sem caracteres especiais para evitar erro de encoding)

Write-Host "--- Iniciando Diagnostico de Ambiente ---" -ForegroundColor Cyan

# 1. Configurar Java 17 (Amazon Corretto)
$correttoRoot = "C:\Program Files\Amazon Corretto"
if (Test-Path $correttoRoot) {
    $jdkFolder = Get-ChildItem -Path $correttoRoot | Where-Object { $_.Name -like "jdk17*" } | Select-Object -First 1
    if ($jdkFolder) {
        $foundJava = $jdkFolder.FullName
        $env:JAVA_HOME = $foundJava
        $env:Path = "$foundJava\bin;" + $env:Path
        Write-Host "Java 17 configurado: $foundJava" -ForegroundColor Green
    }
}

# 2. Configurar Maven 3.9.9 (Encontrado em C:\maven)
$mavenPath = "C:\maven\apache-maven-3.9.9"
if (Test-Path $mavenPath) {
    $env:M2_HOME = $mavenPath
    $env:Path = "$mavenPath\bin;" + $env:Path
    Write-Host "Maven 3.9.9 configurado: $mavenPath" -ForegroundColor Green
} else {
    Write-Host "AVISO: Nao encontrei o Maven em $mavenPath" -ForegroundColor Yellow
}

Write-Host "--- Verificando Versoes ---"
java -version
mvn -version

Write-Host "---------------------------"
Write-Host "Tudo pronto! Agora voce pode rodar: mvn spring-boot:run" -ForegroundColor Cyan
