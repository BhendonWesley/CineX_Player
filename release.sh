#!/bin/bash

# CineX Player - Script de Release
# Uso: ./release.sh 1.0.0
# Isso vai commitar, criar tag v1.0.0 e fazer push

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "========================================="
    echo "  CineX Player - Release"
    echo "========================================="
    echo ""
    echo "  Uso: ./release.sh <versao>"
    echo "  Exemplo: ./release.sh 1.0.0"
    echo ""
    echo "========================================="
    exit 1
fi

echo ""
echo "========================================="
echo "  CineX Player - Release v$VERSION"
echo "========================================="
echo ""

# 1. Mostra o que mudou
echo "[1/5] Verificando alteracoes..."
git status --short
echo ""

# 2. Confirma
read -p "Deseja continuar com o release v$VERSION? (s/n): " CONFIRM
if [ "$CONFIRM" != "s" ]; then
    echo "Release cancelado."
    exit 0
fi

# 3. Adiciona e commita
echo ""
echo "[2/5] Commitando alteracoes..."
git add -A
git commit -m "release: v$VERSION"

# 4. Cria a tag
echo ""
echo "[3/5] Criando tag v$VERSION..."
git tag "v$VERSION"

# 5. Push
echo ""
echo "[4/5] Enviando para o GitHub..."
git push origin main --tags

echo ""
echo "[5/5] Pronto!"
echo ""
echo "========================================="
echo "  Release v$VERSION enviado!"
echo "  O APK sera gerado automaticamente."
echo ""
echo "  Acompanhe em:"
echo "  https://github.com/BhendonWesley/CineX_Player/actions"
echo ""
echo "  Quando pronto, o APK estara em:"
echo "  https://github.com/BhendonWesley/CineX_Player/releases"
echo "========================================="
