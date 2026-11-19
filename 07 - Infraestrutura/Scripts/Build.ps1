param([switch]$BackendOnly, [switch]$CoreOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
try {
    $javaHome = $env:JAVA_HOME
    if (!$javaHome -and (Test-Path 'C:/Program Files/BlueJ/jdk/bin/javac.exe')) {
        $javaHome = 'C:/Program Files/BlueJ/jdk'
    }
    $javac = if ($javaHome) { Join-Path $javaHome 'bin/javac.exe' } else { (Get-Command javac).Source }
    $jar = if ($javaHome) { Join-Path $javaHome 'bin/jar.exe' } else { (Get-Command jar).Source }
    $deps = @{
        'gson-2.13.2.jar' = 'com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'
        'postgresql-42.7.9.jar' = 'org/postgresql/postgresql/42.7.9/postgresql-42.7.9.jar'
        'org.eclipse.paho.client.mqttv3-1.2.5.jar' = 'org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar'
    }
    $dependencies = New-Item -ItemType Directory -Force 'build/dependencies'
    foreach ($name in $deps.Keys) {
        $target = Join-Path $dependencies.FullName $name
        if (!(Test-Path -LiteralPath $target)) {
            curl.exe --fail --silent --show-error --location --retry 2 ("https://repo.maven.apache.org/maven2/" + $deps[$name]) --output "$target.part"
            if ($LASTEXITCODE) { throw "Download falhou: $name" }
            Move-Item -LiteralPath "$target.part" -Destination $target
        }
    }
    function Compile([string]$module, [string[]]$jars, [string[]]$refs) {
        $library = New-Item -ItemType Directory -Force "$module/lib"
        $bluej = New-Item -ItemType Directory -Force "$module/+libs"
        foreach ($dependency in $jars) {
            Copy-Item -LiteralPath $dependency -Destination $library.FullName -Force
            Copy-Item -LiteralPath $dependency -Destination $bluej.FullName -Force
        }
        $output = New-Item -ItemType Directory -Force "build/$module"
        $cp = (@($library.FullName + '/*') + $refs) -join ';'
        $sources = @(Get-ChildItem -LiteralPath "$module/atlas" -Recurse -Filter '*.java' -File | Select-Object -ExpandProperty FullName)
        & $javac --release 21 -encoding UTF-8 -cp $cp -d $output.FullName @sources
        if ($LASTEXITCODE) { throw "Falha de compilação: $module" }
        if (Test-Path -LiteralPath "$module/resources") {
            Copy-Item -Path "$module/resources/*" -Destination $output.FullName -Recurse -Force
            & $jar --create --file (Join-Path $bluej.FullName 'atlas-resources.jar') -C "$module/resources" .
            if ($LASTEXITCODE) { throw "Falha ao empacotar recursos de $module." }
        }
        Write-Host "Compilado: $module"
    }
    $gson = Join-Path $dependencies.FullName 'gson-2.13.2.jar'
    $paho = Join-Path $dependencies.FullName 'org.eclipse.paho.client.mqttv3-1.2.5.jar'
    $postgres = Join-Path $dependencies.FullName 'postgresql-42.7.9.jar'
    Compile '01 - Backend' @($gson,$paho,$postgres) @()
    if ($BackendOnly) { return }
    $fx = $env:ATLAS_JAVAFX_LIB
    if (!$fx) { $fx = 'C:/Program Files/BlueJ/lib' }
    $fxJars = @(Get-ChildItem -LiteralPath $fx -Filter 'javafx*.jar' | Where-Object {
        $_.Name -match '^javafx[.-](base|graphics|controls|fxml|swing)([.-])'
    } | Select-Object -ExpandProperty FullName)
    if ($fxJars.Count -lt 5) { throw 'Defina ATLAS_JAVAFX_LIB com o diretório lib do SDK JavaFX compatível com JDK 21.' }
    Compile '06 - Impressao' @($gson) @()
    Compile '09 - Clientes JavaFX' @($gson,$paho) @($fxJars)
    if ($CoreOnly) { return }
    foreach ($entry in @(@('06 - Impressao','atlas-print.jar'),@('09 - Clientes JavaFX','atlas-fx.jar'))) {
        & $jar --create --file (Join-Path $dependencies.FullName $entry[1]) -C "build/$($entry[0])" .
        if ($LASTEXITCODE) { throw 'Falha ao empacotar biblioteca Java.' }
    }
    $fxShared = Join-Path $dependencies.FullName 'atlas-fx.jar'
    $print = Join-Path $dependencies.FullName 'atlas-print.jar'
    Compile '02 - Desktop' @($gson,$paho,$fxShared) @($fxJars)
    Compile '04 - Terminal Bar' @($gson,$paho,$fxShared) @($fxJars)
    Compile '05 - Terminal Cozinha' @($gson,$paho,$fxShared,$print) @($fxJars)
} finally { Pop-Location }
