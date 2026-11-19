param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9_-]{3,48}$')][string]$User,
    [Parameter(Mandatory)][ValidateSet('BACKEND','GERENTE','CAIXA','GARCOM','BAR','COZINHA')][string]$Role,
    [string]$MosquittoPasswd
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$configuration=Join-Path $root '07 - Infraestrutura/MQTT'
$passwords=Join-Path $configuration 'passwords'
$acl=Join-Path $configuration 'acl'
$create=if (!(Test-Path -LiteralPath $passwords)) { @('-c') } else { @() }
if (!$MosquittoPasswd -and (Test-Path "$root/.local/mosquitto/mosquitto_passwd.exe")) {
    $MosquittoPasswd="$root/.local/mosquitto/mosquitto_passwd.exe"
}
if ($MosquittoPasswd) {
    & $MosquittoPasswd @create $passwords $User
} else {
    docker run --rm -it --user root --entrypoint mosquitto_passwd --mount "type=bind,source=$configuration,target=/provision" eclipse-mosquitto:2 @create /provision/passwords $User
}
if ($LASTEXITCODE) { throw 'O provisionamento da senha MQTT falhou.' }
$topics=switch ($Role) {
    BACKEND { @('topic read atlas/v1/commands/+/+','topic write atlas/v1/stations/+/+','topic write atlas/v1/users/+/CONFIRMAREPEDIDO','topic write atlas/v1/updates') }
    GARCOM { @("topic write atlas/v1/commands/$User/NOVOPEDIDO","topic read atlas/v1/users/$User/CONFIRMAREPEDIDO",'topic read atlas/v1/updates') }
    BAR { @("topic write atlas/v1/commands/$User/CONFIRMABAR",'topic read atlas/v1/stations/BAR/ENVIABAR','topic read atlas/v1/updates') }
    COZINHA { @("topic write atlas/v1/commands/$User/CONFIRMACOZINHA",'topic read atlas/v1/stations/COZINHA/ENVIACOZINHA','topic read atlas/v1/updates') }
    default { @('topic read atlas/v1/updates') }
}
$old=if (Test-Path -LiteralPath $acl) { Get-Content -LiteralPath $acl -Raw } else { '' }
# Um bloco por identidade, terminado pelo início do próximo bloco.
$pattern='(?ms)^user ' + [regex]::Escape($User) + '[ \t]*\r?\n.*?(?=^user |\z)'
$clean=([regex]::Replace($old,$pattern,'')).Trim()
$block=(@("user $User") + $topics) -join [Environment]::NewLine
$content = if ($clean -eq '') { $block } else { $clean + [Environment]::NewLine + [Environment]::NewLine + $block }
[IO.File]::WriteAllText($acl,$content + [Environment]::NewLine)
Write-Host 'Conta provisionada. Reinicie o Mosquitto para carregar as credenciais e permissões.'
