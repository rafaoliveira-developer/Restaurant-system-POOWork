param(
    [Parameter(Mandatory)][ValidateSet('Backend','Desktop','Bar','Cozinha')][string]$Module
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
try {
    if (Test-Path '.env') {
        foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
            if ($line -match '^([A-Z_][A-Z0-9_]*)=(.*)$' -and $null -eq [Environment]::GetEnvironmentVariable($Matches[1],'Process')) {
                [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
            }
        }
    }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' }
        elseif (Test-Path 'C:/Program Files/BlueJ/jdk/bin/java.exe') { 'C:/Program Files/BlueJ/jdk/bin/java.exe' }
        else { (Get-Command java).Source }
    $entry = @{
        Backend = @('01 - Backend','atlas.Backend')
        Desktop = @('02 - Desktop','atlas.desktop.Desktop')
        Bar = @('04 - Terminal Bar','atlas.bar.Bar')
        Cozinha = @('05 - Terminal Cozinha','atlas.cozinha.Cozinha')
    }[$Module]
    if (!(Test-Path -LiteralPath "build/$($entry[0])")) { throw 'Execute Build.ps1 primeiro.' }
    $cp = "build/$($entry[0]);$($entry[0])/lib/*"
    if ($Module -eq 'Backend') { & $java -cp $cp $entry[1] }
    else {
        $fx = $env:ATLAS_JAVAFX_LIB
        if (!$fx) { $fx = 'C:/Program Files/BlueJ/lib' }
        $fxJars = @(Get-ChildItem -LiteralPath $fx -Filter 'javafx*.jar' | Where-Object {
            $_.Name -match '^javafx[.-](base|graphics|controls|fxml|swing)([.-])'
        } | Select-Object -ExpandProperty FullName)
        if ($fxJars.Count -lt 5) { throw 'Defina ATLAS_JAVAFX_LIB.' }
        & $java --module-path ($fxJars -join ';') --add-modules javafx.controls,javafx.fxml,javafx.swing -cp $cp $entry[1]
    }
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
} finally { Pop-Location }
