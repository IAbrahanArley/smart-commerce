param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '../infrastructure/.env'),
    [ValidateSet('dev')]
    [string]$Profile = 'dev'
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw 'Copie infrastructure/.env.example para infrastructure/.env e preencha as senhas locais.'
}

# Formato deliberadamente simples: KEY=value, sem avaliacao de comandos,
# interpolacao, aspas ou valores multilinha. Nunca imprime os valores.
$settings = @{}
foreach ($line in Get-Content -LiteralPath $EnvironmentFile) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=([^\s''"$#]*)$') {
        throw 'Formato de .env invalido. Use KEY=value simples, sem aspas, espacos, $ ou #.'
    }
    $settings[$Matches[1]] = $Matches[2]
}
foreach ($required in @('DB_NAME', 'DB_USERNAME', 'DB_PASSWORD', 'DB_PORT')) {
    if ([string]::IsNullOrWhiteSpace($settings[$required])) {
        throw "Variavel obrigatoria ausente: $required"
    }
}
if ($settings['DB_NAME'] -notmatch '^[a-z][a-z0-9_]*$' -or
    $settings['DB_USERNAME'] -notmatch '^[a-z][a-z0-9_]*$') {
    throw 'DB_NAME e DB_USERNAME devem usar letras minusculas, numeros e underscore.'
}
if ($settings['DB_PORT'] -notmatch '^\d{1,5}$' -or
    [int]$settings['DB_PORT'] -lt 1 -or [int]$settings['DB_PORT'] -gt 65535) {
    throw 'DB_PORT deve estar entre 1 e 65535.'
}

$previous = @{}
foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SPRING_PROFILES_ACTIVE')) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($settings['DB_PORT'])/$($settings['DB_NAME'])"
    $env:DB_USERNAME = $settings['DB_USERNAME']
    $env:DB_PASSWORD = $settings['DB_PASSWORD']
    $env:SPRING_PROFILES_ACTIVE = $Profile
    Push-Location (Join-Path $PSScriptRoot '../backend')
    try {
        & ./mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "Backend encerrou com codigo $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    foreach ($key in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
}
