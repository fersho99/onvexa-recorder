param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
# libpq lee credenciales desde PGSERVICE/.pgpass o las variables PG*. No se imprimen.
if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) { throw 'Instala las herramientas cliente de PostgreSQL para disponer de pg_dump.' }
if (-not $env:PGSERVICE -and (-not $env:PGHOST -or -not $env:PGUSER -or -not $env:PGDATABASE)) { throw 'Configura PGSERVICE o PGHOST, PGUSER y PGDATABASE; usa .pgpass o PGPASSWORD para autenticar.' }
if (-not $env:PGSSLMODE) { $env:PGSSLMODE='require' }
$target=[IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($target) | Out-Null
$file=Join-Path $target ('onvexa-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.dump')
& pg_dump --format=custom --no-owner --no-acl --file $file
if ($LASTEXITCODE -ne 0) { throw 'No se completo el respaldo. Revisa conectividad y credenciales.' }
Get-FileHash -LiteralPath $file -Algorithm SHA256 | Select-Object Path,Hash
