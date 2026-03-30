param(
    [string]$Version,
    [string]$Message
)

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

# 1. Atualiza versao no build.gradle.kts
Write-Host "[1/7] Atualizando versao no build.gradle.kts..."
$gradlePath = "deps\app\build.gradle.kts"
$content = Get-Content $gradlePath -Raw
$content = $content -replace 'versionCode = \d+', "versionCode = $versionCode"
$content = $content -replace 'versionName = ".*?"', "versionName = `"$Version`""
Set-Content $gradlePath $content -NoNewline
Write-Host "  versionName = `"$Version`""
Write-Host "  versionCode = $versionCode"
Write-Host ""

# 2. Salva as notas de release no arquivo
Write-Host "[2/7] Salvando notas de release..."
$releaseNotes = $Message -replace ";", "`n"
Set-Content "RELEASE_NOTES.txt" $releaseNotes -NoNewline
Write-Host "  Notas: $Message"
Write-Host ""

# 3. Mostra o que mudou
Write-Host "[3/7] Verificando alteracoes..."
git status --short
Write-Host ""

# 4. Confirma
$confirm = Read-Host "Deseja continuar com o release v$Version? (s/n)"
if ($confirm -ne "s") {
    Write-Host "Release cancelado."
    exit 0
}

# 5. Adiciona e commita
Write-Host ""
Write-Host "[4/7] Commitando alteracoes..."
git add -A
git commit -m "release: v$Version"

# 6. Cria a tag
Write-Host ""
Write-Host "[5/7] Criando tag v$Version..."
git tag "v$Version"

# 7. Push
Write-Host ""
Write-Host "[6/7] Enviando para o GitHub..."
git push origin main --tags

Write-Host ""
Write-Host "[7/7] Pronto!"
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
