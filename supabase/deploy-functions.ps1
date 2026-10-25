param(
    [Parameter(Mandatory = $true)][string]$ProjectRef,
    [string]$SecretsFile
)
$ErrorActionPreference = 'Stop'
if ($ProjectRef -notmatch '^[a-z]{20}$') { throw 'ProjectRef invalido.' }
if (-not $env:SUPABASE_ACCESS_TOKEN) {
    throw 'Falta SUPABASE_ACCESS_TOKEN en el entorno. Usa un token administrativo local; nunca lo guardes en Git.'
}
function Invoke-Supabase {
    param([string[]]$CliArgs)
    & npx.cmd --yes supabase @CliArgs
    if ($LASTEXITCODE -ne 0) { throw 'Fallo Supabase CLI; revisar el mensaje anterior.' }
}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if ($SecretsFile) {
        $resolvedSecrets = (Resolve-Path -LiteralPath $SecretsFile).Path
        Invoke-Supabase -CliArgs @('secrets','set','--env-file',$resolvedSecrets,'--project-ref',$ProjectRef)
    }
    Invoke-Supabase -CliArgs @('functions','deploy','ai','--project-ref',$ProjectRef)
    Invoke-Supabase -CliArgs @('functions','deploy','delete-account','--project-ref',$ProjectRef)
    Invoke-Supabase -CliArgs @('functions','deploy','maintenance','--project-ref',$ProjectRef)
} finally { Pop-Location }
