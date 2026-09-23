# Instala as dependencias do backend (Maven) e do frontend (npm).
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host "==> Verificando ferramentas"
foreach ($t in "java","node","npm") {
    if (-not (Get-Command $t -ErrorAction SilentlyContinue)) {
        Write-Error "$t nao encontrado no PATH"; exit 1
    }
}

Write-Host "==> Backend: dependencias Maven"
Set-Location "$root\oncar\backend"
if (-not (Test-Path .env) -and (Test-Path .env.example)) {
    Copy-Item .env.example .env; Write-Host "   .env criado a partir de .env.example"
}
.\mvnw.cmd -B dependency:go-offline

Write-Host "==> Frontend: dependencias npm"
Set-Location "$root\oncar\frontend"
if (-not (Test-Path .env.local) -and (Test-Path .env.example)) {
    Copy-Item .env.example .env.local; Write-Host "   .env.local criado a partir de .env.example"
}
npm ci

Set-Location $root
Write-Host "==> Concluido."
