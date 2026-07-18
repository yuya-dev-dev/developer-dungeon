Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'lib/LocalRuntime.psm1') -Force
Test-MavenWrapperIntegrity -RepositoryRoot $root

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$exitCode = 1
try {
    $javaHome = Resolve-RequiredJavaHome
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$(Join-Path $javaHome 'bin');$previousPath"

    & (Join-Path $root 'mvnw.cmd') @args
    $exitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
}

exit $exitCode
