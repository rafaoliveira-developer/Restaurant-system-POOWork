param(
    [Parameter(Mandatory)][string]$PgBin,
    [Parameter(Mandatory)][string]$MosquittoBin,
    [int]$PgPort = 55432,
    [int]$MqttPort = 51883,
    [int]$HttpPort = 58080,
    [switch]$JavaFX,
    [string]$AndroidSerial
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
$mqtt=$null
$backend=$null
try {
    $id=[Guid]::NewGuid().ToString('N')
    $dir=(New-Item -ItemType Directory -Force ".local/integration-$id").FullName.Replace('\','/')
    $db="atlas_it_$($id.Substring(0,10))"
    $secret=[Guid]::NewGuid().ToString('N')
    $base=if ($env:JAVA_HOME) {$env:JAVA_HOME} else {'C:/Program Files/BlueJ/jdk'}
    $java=Join-Path $base 'bin/java.exe'
    $javac=Join-Path $base 'bin/javac.exe'
    $pg=(Resolve-Path -LiteralPath $PgBin).Path
    $mosq=(Resolve-Path -LiteralPath $MosquittoBin).Path
    & (Join-Path $pg 'createdb.exe') -h 127.0.0.1 -p $PgPort -U atlas $db
    if ($LASTEXITCODE) { throw 'Falha ao criar PostgreSQL de integração.' }
    & (Join-Path $mosq 'mosquitto_passwd.exe') -b -c "$dir/passwords" 'atlas-backend' $secret
    foreach ($user in @('garcom','bar','cozinha','gerencia','caixa')) {
        & (Join-Path $mosq 'mosquitto_passwd.exe') -b "$dir/passwords" $user $secret
        if ($LASTEXITCODE) { throw 'Falha ao provisionar conta MQTT de integração.' }
    }
    $acl=Get-Content -Raw -LiteralPath '07 - Infraestrutura/MQTT/acl.example'
    $acl.Replace('-exemplo','') | Set-Content -LiteralPath "$dir/acl" -Encoding ascii
    @"
listener $MqttPort 127.0.0.1
allow_anonymous false
retain_available false
password_file $dir/passwords
acl_file $dir/acl
persistence true
persistence_location $dir/
"@ | Set-Content -LiteralPath "$dir/mosquitto.conf" -Encoding ascii
    $mqtt=Start-Process -FilePath (Join-Path $mosq 'mosquitto.exe') -ArgumentList @('-c',('"' + $dir + '/mosquitto.conf"'),'-v') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dir/mqtt.out" -RedirectStandardError "$dir/mqtt.err"
    $env:ATLAS_DB_URL="jdbc:postgresql://127.0.0.1:$PgPort/$db"
    $env:ATLAS_DB_USER='atlas'
    $env:ATLAS_DB_PASSWORD=$secret
    $env:ATLAS_ADMIN_USER='gerencia'
    $env:ATLAS_ADMIN_PASSWORD=$secret
    $env:ATLAS_BIND='127.0.0.1'
    $env:ATLAS_PORT=[string]$HttpPort
    $env:ATLAS_MQTT_URI="tcp://127.0.0.1:$MqttPort"
    $env:ATLAS_MQTT_USER='atlas-backend'
    $env:ATLAS_MQTT_PASSWORD=$secret
    $env:ATLAS_MQTT_STATE="$dir/backend-mqtt"
    $env:ATLAS_IT_URL="http://127.0.0.1:$HttpPort"
    $env:ATLAS_IT_BROKER=$env:ATLAS_MQTT_URI
    $env:ATLAS_IT_PASSWORD=$secret
    $env:ATLAS_IT_OUTPUT="$dir/comprovantes"
    $env:ATLAS_PRINT_MODE="simulado"
    $env:ATLAS_PRINT_OUTPUT="$dir/terminal-comprovantes"
    $backendCp='"build/01 - Backend;01 - Backend/lib/*"'
    $backend=Start-Process -FilePath $java -ArgumentList @('-cp',$backendCp,'atlas.Backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dir/backend.out" -RedirectStandardError "$dir/backend.err"
    $ready=$false
    for ($i=0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 450
        try {
            $health=Invoke-RestMethod -Uri "$env:ATLAS_IT_URL/api/v1/health" -TimeoutSec 2
            if ($health.mqtt -eq 'OK') { $ready=$true; break }
        } catch { }
        if ($backend.HasExited -or $mqtt.HasExited) { break }
    }
    if (!$ready) { throw "Backend ou MQTT indisponível. Consulte $dir." }
    $cp='build/06 - Impressao;build/09 - Clientes JavaFX;01 - Backend/lib/*'
    & $javac --release 21 -encoding UTF-8 -cp $cp -d $dir '08 - Testes/Integracao/RestauranteIT.java'
    if ($LASTEXITCODE) { throw 'Compilação dos testes falhou.' }
    & $java -ea -cp "$dir;$cp" RestauranteIT
    if ($LASTEXITCODE) { throw "Teste de integração falhou. Consulte $dir." }
    if ($JavaFX) {
        $fx = $env:ATLAS_JAVAFX_LIB
        if (!$fx) { $fx = 'C:/Program Files/BlueJ/lib' }
        $fxJars = @(Get-ChildItem -LiteralPath $fx -Filter 'javafx*.jar' | Where-Object {
            $_.Name -match '^javafx[.-](base|graphics|controls|fxml|swing)([.-])'
        } | Select-Object -ExpandProperty FullName)
        $ui = "$cp;build/02 - Desktop;build/04 - Terminal Bar;build/05 - Terminal Cozinha"
        & $javac --release 21 -encoding UTF-8 --module-path ($fxJars -join ';') --add-modules javafx.controls,javafx.fxml,javafx.swing -cp $ui -d $dir '08 - Testes/Desktop/FxSmoke.java'
        if ($LASTEXITCODE) { throw 'Compilação do teste JavaFX falhou.' }
        & $java --module-path ($fxJars -join ';') --add-modules javafx.controls,javafx.fxml,javafx.swing -cp "$dir;$ui" FxSmoke
        if ($LASTEXITCODE) { throw "Teste JavaFX falhou. Consulte $dir." }
    }
    if ($AndroidSerial) {
        & "$root/08 - Testes/Mobile/Run-Android.ps1" -Serial $AndroidSerial -HttpPort $HttpPort -MqttPort $MqttPort
    }
    Write-Host "Evidência e comprovantes: $dir"
} finally {
    if ($backend -and !$backend.HasExited) { Stop-Process -InputObject $backend }
    if ($mqtt -and !$mqtt.HasExited) { Stop-Process -InputObject $mqtt }
    Pop-Location
}
