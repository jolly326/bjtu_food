param(
    [string]$EnvFile = "",
    [switch]$SkipCompile
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendDir = Join-Path $repoRoot "backend"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return $false
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $idx = $line.IndexOf("=")
        if ($idx -le 0) {
            return
        }

        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }

    return $true
}

if (-not $EnvFile) {
    $backendEnv = Join-Path $backendDir ".env"
    $rootEnv = Join-Path $repoRoot ".env"
    if (Test-Path $backendEnv) {
        $EnvFile = $backendEnv
    } else {
        $EnvFile = $rootEnv
    }
}

if (Import-DotEnv $EnvFile) {
    Write-Host "Loaded environment variables from $EnvFile"
} else {
    Write-Warning "No .env file found at $EnvFile. Using existing environment variables."
}

$required = @("MAIL_HOST", "MAIL_PORT", "MAIL_USERNAME", "MAIL_PASSWORD")
$missing = $required | Where-Object { -not [Environment]::GetEnvironmentVariable($_, "Process") }
if ($missing.Count -gt 0) {
    Write-Warning ("Missing SMTP environment variables: " + ($missing -join ", "))
    Write-Warning "POST /auth/email-code will fail until they are configured."
}

Push-Location $backendDir
try {
    if (-not $SkipCompile) {
        mvn compile
    }
    mvn spring-boot:run
} finally {
    Pop-Location
}
