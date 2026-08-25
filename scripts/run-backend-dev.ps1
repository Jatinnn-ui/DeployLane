<#
.SYNOPSIS
    Starts the backend on Windows with .env loaded into the process environment.

.DESCRIPTION
    application.yml resolves every secret and connection setting from environment variables.
    On Linux/macOS `set -a; source .env` is enough, but PowerShell has no equivalent and Spring Boot
    does not read .env files itself, so starting the backend directly would fall back to the
    built-in defaults and fail startup validation on the missing JWT_SECRET / ENCRYPTION_KEY.

    This script parses .env, exports the values for this process only (nothing is written to the
    user or machine environment), then hands off to Maven.

.EXAMPLE
    ./scripts/run-backend-dev.ps1

.EXAMPLE
    ./scripts/run-backend-dev.ps1 -Goals 'clean spring-boot:run'
#>
param(
    [string]$Goals = 'spring-boot:run'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $root '.env'
$backendPath = Join-Path $root 'backend'

if (-not (Test-Path $envPath)) {
    throw ".env not found. Run ./scripts/generate-secrets.ps1 first."
}

$loaded = 0
foreach ($line in Get-Content -LiteralPath $envPath) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }

    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) { continue }

    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()

    # Strip one layer of surrounding quotes; a base64 secret never needs them but hand-edited
    # .env files often have them.
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    $loaded++
}

Write-Host "Loaded $loaded values from .env into this process." -ForegroundColor Green

# Secrets are only reported as present/absent. Printing them to a console that may be logged or
# screen-shared would defeat the point of encrypting them at rest.
foreach ($required in @('JWT_SECRET', 'ENCRYPTION_KEY')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($required))) {
        throw "$required is empty. Run ./scripts/generate-secrets.ps1."
    }
    Write-Host "  $required is set." -ForegroundColor DarkGray
}

if ([string]::IsNullOrWhiteSpace($env:GITHUB_CLIENT_ID)) {
    Write-Host "GITHUB_CLIENT_ID is empty - the app starts, but GitHub sign-in will be unavailable." -ForegroundColor Yellow
}

# Maven resolution order: PATH, the local toolchain used to build this project, then the wrapper.
$mvn = $null
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvn = 'mvn'
} else {
    $toolchain = Join-Path $env:USERPROFILE '.deployforge-tools\apache-maven-3.9.9\bin\mvn.cmd'
    if (Test-Path $toolchain) {
        $mvn = $toolchain
    } elseif (Test-Path (Join-Path $backendPath 'mvnw.cmd')) {
        $mvn = Join-Path $backendPath 'mvnw.cmd'
    } else {
        throw 'No Maven found on PATH, in .deployforge-tools, or as a wrapper in backend/.'
    }
}

Write-Host "Starting backend with $mvn $Goals" -ForegroundColor Cyan
Push-Location $backendPath
try {
    & cmd /c "`"$mvn`" -B $Goals"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
