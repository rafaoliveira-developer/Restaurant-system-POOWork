param(
    [Parameter(Mandatory)][ValidateSet('Backend','Desktop','Bar','Cozinha','Impressao','Clientes')][string]$Module,
    [string]$Executable='C:/Program Files/BlueJ/BlueJ.exe'
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$folders=@{Backend='01 - Backend';Desktop='02 - Desktop';Bar='04 - Terminal Bar';Cozinha='05 - Terminal Cozinha';Impressao='06 - Impressao';Clientes='09 - Clientes JavaFX'}
$project=Join-Path $root $folders[$Module]
if (!(Test-Path -LiteralPath $Executable)) { throw 'Informe o caminho do BlueJ pelo parâmetro Executable.' }
if (!(Test-Path -LiteralPath "$project/+libs")) { throw 'Execute Build.ps1 primeiro.' }
$envFile=Join-Path $root '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ($line -match '^([A-Z_][A-Z0-9_]*)=(.*)$' -and $null -eq [Environment]::GetEnvironmentVariable($Matches[1],'Process')) {
            [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
        }
    }
}
if (!$env:ATLAS_MIGRATIONS) { $env:ATLAS_MIGRATIONS=Join-Path $root '07 - Infraestrutura/Banco de Dados' }
if (!$env:ATLAS_MQTT_STATE) { $env:ATLAS_MQTT_STATE=Join-Path $root '.local/backend-mqtt' }
if (!$env:ATLAS_PRINT_OUTPUT) { $env:ATLAS_PRINT_OUTPUT=Join-Path $root '.local/comprovantes' }
Start-Process -FilePath $Executable -ArgumentList ('"' + $project + '"') -WorkingDirectory $root
