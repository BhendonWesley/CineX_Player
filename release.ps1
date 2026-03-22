param(
    [string]$Version
)

if (-not $Version) {
    Write-Host ""
    Write-Host "========================================="
    Write-Host "  CineX Player - Release"
    Write-Host "========================================="
    Write-Host ""
    Write-Host "  Uso: .\release.ps1 1.0.0"
    Write-Host ""
    Write-Host "========================================="
    exit 1
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

# 1. Atualiza versao no build.gradle.kts
Write-Host "[1/6] Atualizando versao no build.gradle.kts..."
$gradlePath = "deps\app\build.gradle.kts"
$content = Get-Content $gradlePath -Raw
$content = $content -replace 'versionCode = \d+', "versionCode = $versionCode"
$content = $content -replace 'versionName = ".*?"', "versionName = `"$Version`""
Set-Content $gradlePath $content -NoNewline
Write-Host "  versionName = `"$Version`""
Write-Host "  versionCode = $versionCode"
Write-Host ""

# 2. Mostra o que mudou
Write-Host "[2/6] Verificando alteracoes..."
git status --short
Write-Host ""

# 3. Confirma
$confirm = Read-Host "Deseja continuar com o release v$Version? (s/n)"
if ($confirm -ne "s") {
    Write-Host "Release cancelado."
    exit 0
}

# 4. Adiciona e commita
Write-Host ""
Write-Host "[3/6] Commitando alteracoes..."
git add -A
git commit -m "release: v$Version"

# 5. Cria a tag
Write-Host ""
Write-Host "[4/6] Criando tag v$Version..."
git tag "v$Version"

# 6. Push
Write-Host ""
Write-Host "[5/6] Enviando para o GitHub..."
git push origin main --tags

Write-Host ""
Write-Host "[6/6] Pronto!"
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
