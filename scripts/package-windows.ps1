$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$packager = Get-Command jpackage -ErrorAction SilentlyContinue
if ($null -ne $packager) {
    $jpackagePath = $packager.Source
}
elseif ($env:JAVA_HOME) {
    $jpackagePath = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (-not (Test-Path -LiteralPath $jpackagePath -PathType Leaf)) {
        throw "jpackage.exe was not found under JAVA_HOME: $env:JAVA_HOME"
    }
}
else {
    throw "Install a full JDK and add jpackage.exe to PATH or set JAVA_HOME."
}

Push-Location $projectRoot
try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "test.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "BasinWatch tests failed with exit code $LASTEXITCODE."
    }

    & mvn -q package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven packaging failed with exit code $LASTEXITCODE."
    }

    $jar = Join-Path $projectRoot "target\basinwatch-1.0.0.jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "The expected application JAR does not exist: $jar"
    }

    $packageInput = Join-Path $projectRoot "target\package-input"
    New-Item -ItemType Directory -Path $packageInput -Force | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $packageInput "basinwatch-1.0.0.jar")

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $releaseDirectory = Join-Path $projectRoot "dist\BasinWatch-Windows-$stamp"
    New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null

    & $jpackagePath `
        --type app-image `
        --name BasinWatch `
        --app-version 1.0.0 `
        --vendor BasinWatch `
        --description "Offline floodplain operations console" `
        --input $packageInput `
        --main-jar "basinwatch-1.0.0.jar" `
        --main-class "com.basinwatch.app.BasinWatchApp" `
        --dest $releaseDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $applicationImage = Join-Path $releaseDirectory "BasinWatch"
    $launcher = Join-Path $applicationImage "BasinWatch.exe"
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw "jpackage completed without creating the expected launcher: $launcher"
    }

    $zipPath = Join-Path $releaseDirectory "BasinWatch-Windows.zip"
    Compress-Archive -Path $applicationImage -DestinationPath $zipPath -CompressionLevel Optimal
    if (-not (Test-Path -LiteralPath $zipPath -PathType Leaf)) {
        throw "Could not create the portable BasinWatch ZIP: $zipPath"
    }

    Write-Output "Portable application image: $applicationImage"
    Write-Output "Distributable ZIP: $zipPath"
    Write-Output "The recipient can extract the complete BasinWatch folder and run BasinWatch.exe; no Java install is required."
}
finally {
    Pop-Location
}
