param(
    [string]$Version,
    [string]$Message
)

$ErrorActionPreference = "Stop"

function Get-PreferredJavaHome {
    $candidates = @()

    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }

    $candidates += @(
        "C:\Program Files\Android\Android Studio1\jbr",
        "C:\Program Files\Android\Android Studio\jbr"
    )

    foreach ($candidate in $candidates) {
        if (-not $candidate) {
            continue
        }

        $javaExe = Join-Path $candidate "bin\java.exe"
        if (Test-Path $javaExe) {
            return $candidate
        }
    }

    return $null
}

function Assert-LastExitCode {
    param(
        [string]$StepName
    )

    if ($LASTEXITCODE -ne 0) {
        throw "$StepName falhou com codigo $LASTEXITCODE."
    }
}

if (-not $Version) {
    Write-Host ""
    Write-Host "========================================="
    Write-Host "  CineX Player - Release"
    Write-Host "========================================="
    Write-Host ""
    Write-Host "  Uso: .\release.ps1 1.0.0"
    Write-Host "  Uso: .\release.ps1 1.0.0 -Message 'Novo player de video'"
    Write-Host ""
    Write-Host "========================================="
    exit 1
}

# Pede a mensagem se nao foi passada como parametro
if (-not $Message) {
    Write-Host ""
    Write-Host "Digite as notas de release (o que mudou nesta versao)."
    Write-Host "Para multiplas linhas, separe com ;  Ex: Novo player;Correcao de bugs"
    Write-Host ""
    $Message = Read-Host "Notas de release"
}

if (-not $Message) {
    $Message = "Melhorias de desempenho e correcoes de bugs."
}

# Calcula versionCode a partir da versao (1.0.0 = 10000, 1.0.1 = 10001, 1.2 = 10200)
$parts = $Version.Split(".")
$major = [int]$parts[0]
$minor = [int]$parts[1]
$patch = if ($parts.Count -ge 3) { [int]$parts[2] } else { 0 }
$versionCode = $major * 10000 + $minor * 100 + $patch

Write-Host ""
Write-Host "========================================="
Write-Host "  CineX Player - Release v$Version"
Write-Host "  versionCode: $versionCode"
Write-Host "========================================="
Write-Host ""

$javaHome = Get-PreferredJavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
    Write-Host "[env] JAVA_HOME configurado para: $javaHome"
} else {
    Write-Host "[env] JAVA_HOME nao encontrado automaticamente. Usando o Java atual do sistema."
}

$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot "deps\.gradle-user"
if (-not (Test-Path $env:GRADLE_USER_HOME)) {
    New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME | Out-Null
}
Write-Host "[env] GRADLE_USER_HOME: $env:GRADLE_USER_HOME"
Write-Host ""

# 1. Atualiza versao no build.gradle.kts
Write-Host "[1/8] Atualizando versao no build.gradle.kts..."
$gradlePath = "deps\app\build.gradle.kts"
$content = Get-Content $gradlePath -Raw
$content = $content -replace 'versionCode = \d+', "versionCode = $versionCode"
$content = $content -replace 'versionName = ".*?"', "versionName = `"$Version`""
Set-Content $gradlePath $content -NoNewline
Write-Host "  versionName = `"$Version`""
Write-Host "  versionCode = $versionCode"
Write-Host ""

# 2. Salva as notas de release no arquivo
Write-Host "[2/8] Salvando notas de release..."
$releaseNotes = $Message -replace ";", "`n"
Set-Content "RELEASE_NOTES.txt" $releaseNotes -NoNewline
Write-Host "  Notas: $Message"
Write-Host ""

# 3. Valida o build antes de commitar e criar tag
Write-Host "[3/8] Validando build local..."
Push-Location "deps"
& ".\gradlew.bat" "app:assembleDebug" "--no-daemon"
$buildExitCode = $LASTEXITCODE
Pop-Location

if ($buildExitCode -ne 0) {
    Write-Host ""
    Write-Host "Build falhou. Release cancelado antes de commitar, criar tag ou enviar para o GitHub."
    exit $buildExitCode
}

Write-Host "  Build OK."
Write-Host ""

# 4. Mostra o que mudou
Write-Host "[4/8] Verificando alteracoes..."
git status --short
Write-Host ""

# 5. Confirma
$confirm = Read-Host "Deseja continuar com o release v$Version? (s/n)"
if ($confirm -ne "s") {
    Write-Host "Release cancelado."
    exit 0
}

# 6. Adiciona e commita
Write-Host ""
Write-Host "[5/8] Commitando alteracoes..."
git add -A
Assert-LastExitCode "git add"
git commit -m "release: v$Version"
Assert-LastExitCode "git commit"

# 7. Cria a tag
Write-Host ""
Write-Host "[6/8] Criando tag v$Version..."
if (git rev-parse "v$Version" 2>$null) {
    throw "A tag v$Version ja existe. Apague a tag antiga antes de continuar."
}
git tag "v$Version"
Assert-LastExitCode "git tag"

# 8. Push
Write-Host ""
Write-Host "[7/8] Enviando para o GitHub..."
git push origin main --tags
Assert-LastExitCode "git push"

Write-Host ""
Write-Host "[8/8] Pronto!"
Write-Host ""
Write-Host "========================================="
Write-Host "  Release v$Version enviado!"
Write-Host "  O APK sera gerado automaticamente."
Write-Host ""
Write-Host "  Acompanhe em:"
Write-Host "  https://github.com/BhendonWesley/CineX_Player/actions"
Write-Host ""
Write-Host "  Quando pronto, o APK estara em:"
Write-Host "  https://github.com/BhendonWesley/CineX_Player/releases"
Write-Host "========================================="
