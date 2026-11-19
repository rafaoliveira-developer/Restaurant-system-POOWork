param(
  [Parameter(Mandatory)][string]$Serial,
  [Parameter(Mandatory)][int]$HttpPort,
  [Parameter(Mandatory)][int]$MqttPort
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$env:ATLAS_IT_PASSWORD) { throw 'Execute pelo runner de integração com -AndroidSerial.' }
if (!$env:JAVA_HOME -and (Test-Path 'C:/Program Files/BlueJ/jdk')) { $env:JAVA_HOME='C:/Program Files/BlueJ/jdk' }
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME=Join-Path $root '.local/android-sdk' }
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME=Join-Path $root '.local/gradle-home' }
if (!$env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME=Join-Path $root '.local/android-user' }
$adb=Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$httpRegistered=$false
$mqttRegistered=$false
Push-Location (Join-Path $root '03 - Mobile/AndroidApp')
try {
    & '.\gradlew.bat' --no-daemon ':app:assembleDebug' ':app:assembleDebugAndroidTest'
    if ($LASTEXITCODE) { throw 'Falha ao compilar APKs de validação.' }
    & $adb -s $Serial reverse "tcp:$HttpPort" "tcp:$HttpPort"
    if ($LASTEXITCODE) { throw 'Falha ao encaminhar a porta HTTP de integração.' }
    $httpRegistered=$true
    & $adb -s $Serial reverse "tcp:$MqttPort" "tcp:$MqttPort"
    if ($LASTEXITCODE) { throw 'Falha ao encaminhar a porta MQTT de integração.' }
    $mqttRegistered=$true
    & $adb -s $Serial install -r 'app/build/outputs/apk/debug/app-debug.apk'
    if ($LASTEXITCODE) { throw 'Falha ao instalar APK de depuração.' }
    & $adb -s $Serial install -r 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if ($LASTEXITCODE) { throw 'Falha ao instalar APK de instrumentação.' }
    $result= & $adb -s $Serial shell am instrument -w -r -e class com.example.pooproject.FluxoAtlasTest -e atlasApi "http://127.0.0.1:$HttpPort" -e atlasMqtt "tcp://127.0.0.1:$MqttPort" -e atlasPassword $env:ATLAS_IT_PASSWORD 'com.example.pooproject.test/androidx.test.runner.AndroidJUnitRunner'
    if ($LASTEXITCODE -or -not ($result -match 'OK \(1 test\)')) { ($result | ForEach-Object { $_.Replace($env:ATLAS_IT_PASSWORD,"[redigido]") }) | Write-Host; throw 'O fluxo Android falhou.' }
    $result | Where-Object { $_ -match 'OK \(' } | Write-Host
} finally {
    if ($httpRegistered) { & $adb -s $Serial reverse --remove "tcp:$HttpPort" }
    if ($mqttRegistered) { & $adb -s $Serial reverse --remove "tcp:$MqttPort" }
    Pop-Location
}
