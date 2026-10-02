$ErrorActionPreference = "Stop"

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    mvn -q test-compile
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test compilation failed with exit code $LASTEXITCODE."
    }

    $classpath = "target\classes;target\test-classes"
    java -ea -cp $classpath com.basinwatch.InvariantSuite
    if ($LASTEXITCODE -ne 0) {
        throw "Invariant suite failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
