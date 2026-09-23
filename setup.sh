#!/usr/bin/env bash
# Instala as dependencias do backend (Maven) e do frontend (npm).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Verificando ferramentas"
command -v java >/dev/null || { echo "ERRO: JDK 17 nao encontrado"; exit 1; }
command -v node >/dev/null || { echo "ERRO: Node.js 20+ nao encontrado"; exit 1; }
command -v npm  >/dev/null || { echo "ERRO: npm nao encontrado"; exit 1; }

echo "==> Backend: dependencias Maven"
cd "$ROOT/oncar/backend"
[ -f .env ] || { [ -f .env.example ] && cp .env.example .env && echo "   .env criado a partir de .env.example"; }
./mvnw -B dependency:go-offline

echo "==> Frontend: dependencias npm"
cd "$ROOT/oncar/frontend"
[ -f .env.local ] || { [ -f .env.example ] && cp .env.example .env.local && echo "   .env.local criado a partir de .env.example"; }
npm ci

echo "==> Concluido."
