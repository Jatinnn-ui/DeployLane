<#
.SYNOPSIS
    Creates .env from .env.example with freshly generated secrets.

.DESCRIPTION
    DeployForge refuses to start without JWT_SECRET and ENCRYPTION_KEY, because GitHub tokens and
    environment variables are encrypted with the latter. This script generates both with a
    cryptographic RNG and writes them into .env, leaving every other value untouched.

    Existing .env files are never overwritten - losing ENCRYPTION_KEY makes stored secrets
    unrecoverable.

.EXAMPLE
    ./scripts/generate-secrets.ps1
#>
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $root '.env.example'
$envPath = Join-Path $root '.env'

if (-not (Test-Path $examplePath)) {
    throw ".env.example not found at $examplePath"
}

if ((Test-Path $envPath) -and -not $Force) {
    Write-Host ".env already exists. Refusing to overwrite it." -ForegroundColor Yellow
    Write-Host "Losing ENCRYPTION_KEY makes every stored secret unrecoverable." -ForegroundColor Yellow
    Write-Host "Re-run with -Force only if you are certain." -ForegroundColor Yellow
    exit 1
}

function New-RandomBase64([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    # Create()/GetBytes() rather than the static Fill(): Fill() only exists on .NET 5+, so it
    # throws under Windows PowerShell 5.1, which is still the default shell on Windows.
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

# HS256 needs at least 32 bytes; 48 gives margin. AES-256 needs exactly 32.
$jwtSecret = New-RandomBase64 48
$encryptionKey = New-RandomBase64 32

$content = Get-Content -Raw -LiteralPath $examplePath
$content = $content -replace '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$jwtSecret"
$content = $content -replace '(?m)^ENCRYPTION_KEY=.*$', "ENCRYPTION_KEY=$encryptionKey"

Set-Content -LiteralPath $envPath -Value $content -NoNewline

Write-Host "Wrote $envPath with generated JWT_SECRET and ENCRYPTION_KEY." -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Create a GitHub OAuth app and set GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET."
Write-Host "     Callback URL: http://localhost:8080/api/v1/auth/github/callback"
Write-Host "  2. docker compose up -d postgres redis"
Write-Host "  3. cd backend; ./mvnw spring-boot:run"
Write-Host "  4. cd frontend; npm install; npm run dev"
Write-Host ""
Write-Host "Back up ENCRYPTION_KEY. Without it, stored GitHub tokens and environment" -ForegroundColor Yellow
Write-Host "variables cannot be decrypted." -ForegroundColor Yellow
