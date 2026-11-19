param([ValidateSet('Start','Stop')][string]$Action='Start')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
try {
    $state=(New-Item -ItemType Directory -Force '.local/portable').FullName.Replace('\','/')
    $pg="$root/.local/pgsql/bin"
    $mqtt="$root/.local/mosquitto"
    $pgPort=55433
    if (!(Test-Path "$pg/initdb.exe") -or !(Test-Path "$mqtt/mosquitto.exe")) {
        throw 'Binários portáteis ausentes. Use Docker Compose ou extraia PostgreSQL em .local/pgsql e Mosquitto em .local/mosquitto.'
    }
    function Stop-AtlasProcess([string]$name,[string]$expected) {
        $file="$state/$name.pid"
        if (!(Test-Path -LiteralPath $file)) { return }
        $number=[int](Get-Content -LiteralPath $file)
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$number"
        if ($process -and $process.CommandLine.Replace('\\','/') -like "*$expected*") { Stop-Process -Id $number }
        Remove-Item -LiteralPath $file
    }
    if ($Action -eq 'Stop') {
        Stop-AtlasProcess 'backend' 'atlas.Backend'
        Stop-AtlasProcess 'broker' "$state/mosquitto.conf"
        if (Test-Path "$state/pg-data/postmaster.pid") {
            & "$pg/pg_ctl.exe" stop -D "$state/pg-data" -m fast -w
        }
        return
    }
    if (Test-Path "$state/backend.pid") { throw 'Execute Stop antes de iniciar outra instância portátil.' }
    if (!(Test-Path 'build/01 - Backend/atlas/Backend.class')) { & "$PSScriptRoot/Build.ps1" }
    $envFile="$root/.env"
    $config="$root/07 - Infraestrutura/MQTT"
    if (!(Test-Path -LiteralPath $envFile)) {
        if ((Test-Path "$config/passwords") -or (Test-Path "$config/acl")) {
            throw 'Já há um broker provisionado. Crie .env com as credenciais existentes antes de iniciar.'
        }
        $databasePassword=[Guid]::NewGuid().ToString('N')
        $brokerPassword=[Guid]::NewGuid().ToString('N')
        $adminPassword=[Guid]::NewGuid().ToString('N')
        New-Item -ItemType Directory -Force $config | Out-Null
        & "$mqtt/mosquitto_passwd.exe" -c -b "$config/passwords" atlas-backend $brokerPassword
        if ($LASTEXITCODE) { throw 'Falha ao criar identidade MQTT do Backend.' }
        $adminBrokerPassword=[Guid]::NewGuid().ToString('N')
        & "$mqtt/mosquitto_passwd.exe" -b "$config/passwords" gerencia $adminBrokerPassword
        if ($LASTEXITCODE) { throw 'Falha ao criar identidade MQTT da gerência.' }
        @"
user atlas-backend
topic read atlas/v1/commands/+/+
topic write atlas/v1/stations/+/+
topic write atlas/v1/users/+/CONFIRMAREPEDIDO
topic write atlas/v1/updates

user gerencia
topic read atlas/v1/updates
"@ | Set-Content -LiteralPath "$config/acl" -Encoding ascii
        @"
ATLAS_DB_PASSWORD=$databasePassword
POSTGRES_PASSWORD=$databasePassword
ATLAS_DB_URL=jdbc:postgresql://127.0.0.1:$pgPort/atlas?connectTimeout=4
ATLAS_DB_USER=atlas
ATLAS_ADMIN_USER=gerencia
ATLAS_ADMIN_PASSWORD=$adminPassword
ATLAS_MQTT_USER=atlas-backend
ATLAS_MQTT_PASSWORD=$brokerPassword
ATLAS_MQTT_URI=tcp://127.0.0.1:1883
ATLAS_BIND=127.0.0.1
ATLAS_PORT=8080
ATLAS_API_URL=http://127.0.0.1:8080
ATLAS_PRINT_MODE=simulado
"@ | Set-Content -LiteralPath $envFile -Encoding ascii
        "Usuário: gerencia`nSenha da API: $adminPassword`nSenha MQTT: $adminBrokerPassword" |
            Set-Content -LiteralPath "$state/gerencia.txt" -Encoding UTF8
    }
    $configuration=@{}
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ($line -match '^([A-Z_][A-Z0-9_]*)=(.*)$') { $configuration[$Matches[1]]=$Matches[2] }
    }
    if ($configuration.ATLAS_DB_URL -notlike "jdbc:postgresql://127.0.0.1:$pgPort/atlas*") {
        throw "Para a instância portátil use PostgreSQL na porta $pgPort."
    }
    if (!(Test-Path "$state/pg-data/PG_VERSION")) {
        if (!$configuration.ATLAS_DB_PASSWORD) { throw 'Defina ATLAS_DB_PASSWORD em .env.' }
        $passwordFile="$state/init-password"
        try {
            Set-Content -LiteralPath $passwordFile -Value $configuration.ATLAS_DB_PASSWORD -Encoding ascii
            & "$pg/initdb.exe" -D "$state/pg-data" -U atlas -A scram-sha-256 "--pwfile=$passwordFile" -E UTF8 --locale=C | Out-Host
            if ($LASTEXITCODE) { throw 'Falha ao inicializar PostgreSQL.' }
        } finally { if (Test-Path $passwordFile) { Remove-Item -LiteralPath $passwordFile } }
    }
    & "$pg/pg_isready.exe" -h 127.0.0.1 -p $pgPort -U atlas -d postgres -q
    if ($LASTEXITCODE) {
        Start-Process -FilePath "$pg/postgres.exe" -ArgumentList @('-D',('"' + $state + '/pg-data"'),'-p',$pgPort,'-h','127.0.0.1') -WindowStyle Hidden -RedirectStandardOutput "$state/postgres.out" -RedirectStandardError "$state/postgres.err"
    }
    $databaseReady=$false
    for ($i=0; $i -lt 24; $i++) {
        & "$pg/pg_isready.exe" -h 127.0.0.1 -p $pgPort -U atlas -d postgres -q
        if (!$LASTEXITCODE) { $databaseReady=$true; break }
        Start-Sleep -Milliseconds 400
    }
    if (!$databaseReady) { throw "PostgreSQL portátil indisponível. Confira $state/postgres.err." }
    $oldPassword=$env:PGPASSWORD
    try {
        $env:PGPASSWORD=$configuration.ATLAS_DB_PASSWORD
        $exists=& "$pg/psql.exe" -h 127.0.0.1 -p $pgPort -U atlas -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='atlas'"
        if ($LASTEXITCODE) { throw 'Falha de autenticação PostgreSQL.' }
        if ($exists -ne '1') {
            & "$pg/createdb.exe" -h 127.0.0.1 -p $pgPort -U atlas atlas
            if ($LASTEXITCODE) { throw 'Falha ao criar o banco ATLAS.' }
        }
    } finally { $env:PGPASSWORD=$oldPassword }
    $config=$config.Replace('\','/')
    @"
listener 1883 127.0.0.1
allow_anonymous false
retain_available false
password_file $config/passwords
acl_file $config/acl
persistence true
persistence_location $state/
persistent_client_expiration 7d
max_packet_size 73728
"@ | Set-Content -LiteralPath "$state/mosquitto.conf" -Encoding ascii
    $broker=Start-Process -FilePath "$mqtt/mosquitto.exe" -ArgumentList @('-c',('"' + $state + '/mosquitto.conf"')) -PassThru -WindowStyle Hidden -RedirectStandardOutput "$state/broker.out" -RedirectStandardError "$state/broker.err"
    Set-Content -LiteralPath "$state/broker.pid" -Value $broker.Id
    $java=if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'C:/Program Files/BlueJ/jdk/bin/java.exe' }
    foreach ($item in $configuration.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($item.Key,$item.Value,'Process')
    }
    $backend=Start-Process -FilePath $java -ArgumentList @('-cp','"build/01 - Backend;01 - Backend/lib/*"','atlas.Backend') -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput "$state/backend.out" -RedirectStandardError "$state/backend.err"
    Set-Content -LiteralPath "$state/backend.pid" -Value $backend.Id
    for ($i=0; $i -lt 25; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $health=Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 2
            if ($health.mqtt -eq 'OK') {
                Write-Host 'ATLAS local disponível em http://127.0.0.1:8080 e tcp://127.0.0.1:1883.'
                if (Test-Path "$state/gerencia.txt") { Write-Host "Credenciais locais em $state/gerencia.txt" }
                return
            }
        } catch { }
        if ($backend.HasExited -or $broker.HasExited) { break }
    }
    throw "Inicialização incompleta. Confira os logs em $state."
} finally { Pop-Location }
