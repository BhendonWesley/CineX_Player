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

Write-Host ""
Write-Host "========================================="
Write-Host "  CineX Player - Release v$Version"
Write-Host "========================================="
Write-Host ""

# 1. Mostra o que mudou
Write-Host "[1/5] Verificando alteracoes..."
git status --short
Write-Host ""

# 2. Confirma
$confirm = Read-Host "Deseja continuar com o release v$Version? (s/n)"
if ($confirm -ne "s") {
    Write-Host "Release cancelado."
    exit 0
}

# 3. Adiciona e commita
Write-Host ""
Write-Host "[2/5] Commitando alteracoes..."
git add -A
git commit -m "release: v$Version"

# 4. Cria a tag
Write-Host ""
Write-Host "[3/5] Criando tag v$Version..."
git tag "v$Version"

# 5. Push
Write-Host ""
Write-Host "[4/5] Enviando para o GitHub..."
git push origin main --tags

Write-Host ""
Write-Host "[5/5] Pronto!"
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
