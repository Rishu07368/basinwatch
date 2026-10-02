$ErrorActionPreference = "Stop"

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    mvn -q test-compile dependency:build-classpath "-Dmdep.outputFile=target\test-classpath.txt" "-Dmdep.includeScope=test"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test compilation or runtime classpath resolution failed with exit code $LASTEXITCODE."
    }

    $dependencies = (Get-Content -LiteralPath "target\test-classpath.txt" -Raw).Trim()
    $classpath = "target\classes;target\test-classes;$dependencies"
    java -ea -cp $classpath com.basinwatch.InvariantSuite
    if ($LASTEXITCODE -ne 0) {
        throw "Invariant suite failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
