Set-StrictMode -Version Latest

function Get-DockerExecutable {
    $docker = Get-Command docker.exe -CommandType Application -ErrorAction Stop
    $path = [IO.Path]::GetFullPath($docker.Source)
    if (-not [IO.Path]::IsPathFullyQualified($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Docker CLI path is invalid.'
    }
    return $path
}

function Test-MavenWrapperIntegrity {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RepositoryRoot)
    $manifestPath = Join-Path $RepositoryRoot '.mvn/wrapper/wrapper-files.sha256'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Maven Wrapper checksum manifest is missing.' }
    $expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $null = $expected.Add('mvnw'); $null = $expected.Add('mvnw.cmd'); $null = $expected.Add('.mvn/wrapper/maven-wrapper.properties')
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in Get-Content -LiteralPath $manifestPath) {
        $parts = $line -split '\s{2}', 2
        if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9a-f]{64}$' -or $parts[1] -notmatch '^[.a-zA-Z0-9_/-]+$') {
            throw 'Invalid Maven Wrapper checksum manifest.'
        }
        if (-not $seen.Add($parts[1])) { throw "Duplicate Maven Wrapper manifest entry: $($parts[1])" }
        $file = Join-Path $RepositoryRoot $parts[1].Replace('/', '\\')
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Maven Wrapper file is missing: $($parts[1])" }
        $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $parts[0]) { throw "Maven Wrapper checksum mismatch: $($parts[1])" }
    }
    if (-not $seen.SetEquals($expected)) { throw 'Maven Wrapper checksum manifest has an unexpected file set.' }
}

function Test-LocalRuntimePrerequisites {
    [CmdletBinding()]
    param([switch]$RequireJava)
    if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.ToString() -ne '7.6.3' -or [Environment]::Is64BitProcess -ne $true) {
        throw 'PowerShell 7.6.3 x64 is required.'
    }
    if (-not [Environment]::Is64BitOperatingSystem -or [Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or [Environment]::OSVersion.Version.Build -lt 22000) {
        throw 'Windows 11 x64 is required.'
    }
    $wslOutput = & wsl.exe --version 2>&1
    $wslExitCode = $LASTEXITCODE
    $wsl = ($wslOutput | Out-String) -replace "`0", ''
    if ($wslExitCode -ne 0) { throw 'WSL 2.1.5 or later is required.' }
    $wslVersionLine = $wsl -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($wslVersionLine)) { throw 'WSL 2.1.5 or later is required.' }
    $wslMatch = [regex]::Match($wslVersionLine, '(\d+)\.(\d+)\.(\d+)(?:\.\d+)?')
    if (-not $wslMatch.Success) { throw 'WSL 2.1.5 or later is required.' }
    $wslMajor = [int]$wslMatch.Groups[1].Value; $wslMinor = [int]$wslMatch.Groups[2].Value; $wslPatch = [int]$wslMatch.Groups[3].Value
    if ($wslMajor -ne 2 -or $wslMinor -lt 1 -or ($wslMinor -eq 1 -and $wslPatch -lt 5)) { throw 'WSL 2.1.5 or later is required.' }
    $dockerExecutable = Get-DockerExecutable
    $desktop = (& $dockerExecutable version --format '{{.Server.Platform.Name}}' 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $desktop -notmatch '^Docker Desktop 4\.79\.0(?:\s|\(|$)') { throw 'Docker Desktop 4.79.0 is required.' }
    $docker = (& $dockerExecutable version --format '{{.Server.Version}}' 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($docker)) { throw 'Docker Desktop daemon is unavailable.' }
    $platform = (& $dockerExecutable info --format '{{.OSType}}/{{.Architecture}}' 2>&1 | Out-String).Trim()
    if ($platform -notin @('linux/x86_64', 'linux/amd64')) { throw 'Linux amd64 Docker mode is required.' }
    if ($RequireJava) {
        if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is required.' }
        $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $java)) { throw 'JAVA_HOME does not contain java.exe.' }
        $version = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
        if ($version -notmatch 'Temurin-25\.0\.3\+9' -or $version -notmatch 'java\.vendor = Eclipse Adoptium' -or $version -notmatch 'sun\.arch\.data\.model = 64') {
            throw 'Eclipse Temurin 25.0.3+9 x64 is required.'
        }
    }
}

function Get-ChallengeBuildFingerprint {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RepositoryRoot)
    $relative = @('challenge-image/Dockerfile', 'challenge-image/.dockerignore', 'scripts/build-challenge-image.ps1', 'scripts/lib/LocalRuntime.psm1')
    $contextRoot = Join-Path $RepositoryRoot 'challenge-image'
    Get-ChildItem -LiteralPath $contextRoot -Force -Recurse | ForEach-Object {
        $path = $_.FullName.Substring($RepositoryRoot.Length + 1).Replace('\', '/')
        if ($path -notin @('challenge-image/Dockerfile', 'challenge-image/.dockerignore', 'challenge-image/rootfs', 'challenge-image/fixtures') -and
            -not $path.StartsWith('challenge-image/rootfs/') -and -not $path.StartsWith('challenge-image/fixtures/')) {
            throw "Unexpected challenge build-context entry: $path"
        }
        if ($_.LinkType) { throw "Links are not allowed in challenge build context: $path" }
    }
    foreach ($directory in @('challenge-image/rootfs', 'challenge-image/fixtures')) {
        $absolute = Join-Path $RepositoryRoot $directory
        if (-not (Test-Path -LiteralPath $absolute -PathType Container)) { throw "Missing build input directory: $directory" }
        $relative += Get-ChildItem -LiteralPath $absolute -File -Recurse | ForEach-Object { $_.FullName.Substring($RepositoryRoot.Length + 1).Replace('\', '/') }
    }
    $lines = foreach ($path in $relative | Sort-Object) {
        if ($path -notmatch '^[a-z0-9._/-]+$') { throw "Invalid build input path: $path" }
        $file = Join-Path $RepositoryRoot $path.Replace('/', '\')
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing build input: $path" }
        "$( (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() )  $path"
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes((($lines -join "`n") + "`n"))
    return ([Security.Cryptography.SHA256]::HashData($bytes) | ForEach-Object ToString x2) -join ''
}

Export-ModuleMember -Function Get-DockerExecutable, Test-MavenWrapperIntegrity, Test-LocalRuntimePrerequisites, Get-ChallengeBuildFingerprint
