Set-StrictMode -Version Latest

function Get-DockerExecutable {
    $docker = Get-Command docker.exe -CommandType Application -ErrorAction Stop
    $path = [IO.Path]::GetFullPath($docker.Source)
    if (-not [IO.Path]::IsPathFullyQualified($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Docker CLI path is invalid.'
    }
    return $path
}

function Resolve-RequiredJavaHome {
    [CmdletBinding()]
    param([string[]]$CandidateHomes)
    if (-not $PSBoundParameters.ContainsKey('CandidateHomes')) {
        $candidates = [Collections.Generic.List[string]]::new()
        foreach ($candidate in @(
            $env:JAVA_HOME,
            [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
            [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
        )) {
            if (-not [string]::IsNullOrWhiteSpace($candidate)) { $candidates.Add($candidate) }
        }
        $pathJava = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue
        if ($null -ne $pathJava) { $candidates.Add((Split-Path -Parent (Split-Path -Parent $pathJava.Source))) }
        if ($env:ProgramFiles) { $candidates.Add((Join-Path $env:ProgramFiles 'Eclipse Adoptium\jdk-25.0.3.9-hotspot')) }
        $CandidateHomes = $candidates.ToArray()
    }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($candidate in $CandidateHomes) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        try { $home = [IO.Path]::GetFullPath($candidate.Trim()) } catch { continue }
        if (-not $seen.Add($home) -or -not [IO.Path]::IsPathFullyQualified($home) -or -not (Test-Path -LiteralPath $home -PathType Container)) { continue }
        $java = Join-Path $home 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { continue }
        $version = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -and $version -match 'Temurin-25\.0\.3\+9' -and $version -match 'java\.vendor = Eclipse Adoptium' -and $version -match 'sun\.arch\.data\.model = 64') {
            return $home.TrimEnd('\')
        }
    }
    throw 'Eclipse Temurin 25.0.3+9 x64 is required.'
}

function Enter-LocalRuntimeLock {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RuntimeDirectory)
    New-Item -ItemType Directory -Force -Path $RuntimeDirectory | Out-Null
    $lockPath = Join-Path $RuntimeDirectory 'local-runtime.lock'
    if (Test-Path -LiteralPath $lockPath) {
        $item = Get-Item -LiteralPath $lockPath -Force
        if ($item.PSIsContainer -or (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) { throw 'Local runtime lock path is unsafe.' }
    }
    try {
        $stream = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $payload = [Text.Encoding]::UTF8.GetBytes("pid=$PID`nstarted=$([DateTimeOffset]::UtcNow.ToString('O'))`n")
        $stream.SetLength(0); $stream.Write($payload, 0, $payload.Length); $stream.Flush($true)
        return $stream
    } catch [IO.IOException] {
        throw 'Developer Dungeon is already running in this repository.'
    }
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
        $env:JAVA_HOME = Resolve-RequiredJavaHome
    }
}

function New-RunnerToken {
    [CmdletBinding()]
    param()
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $token = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    if ($token -notmatch '^[A-Za-z0-9_-]{43}$') { throw 'Failed to generate Runner token.' }
    return $token
}

function Initialize-DatabaseSecrets {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RuntimeDirectory,
        [Parameter(Mandatory)][string]$DockerExecutable
    )
    $paths = [ordered]@{
        Admin = Join-Path $RuntimeDirectory 'db-admin-password'
        Migrator = Join-Path $RuntimeDirectory 'db-migrator-password'
        App = Join-Path $RuntimeDirectory 'db-app-password'
    }
    $missing = @($paths.Values | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
    if ($missing.Count -gt 0) {
        & $DockerExecutable volume inspect developer-dungeon-postgres-data *> $null
        if ($LASTEXITCODE -eq 0) { throw 'Database credential files are missing while the persistent volume exists. Recovery is required.' }
        if ($missing.Count -ne $paths.Count) { throw 'Database credential files are incomplete.' }
        New-Item -ItemType Directory -Force -Path $RuntimeDirectory | Out-Null
        foreach ($path in $paths.Values) {
            [IO.File]::WriteAllText($path, (New-RunnerToken), [Text.UTF8Encoding]::new($false))
            $security = [Security.AccessControl.FileSecurity]::new()
            $security.SetAccessRuleProtection($true, $false)
            $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
            $systemSid = [Security.Principal.SecurityIdentifier]::new([Security.Principal.WellKnownSidType]::LocalSystemSid, $null)
            $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($currentSid, 'FullControl', 'Allow'))
            $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($systemSid, 'FullControl', 'Allow'))
            Set-Acl -LiteralPath $path -AclObject $security
        }
    }
    $values = [ordered]@{}
    foreach ($name in $paths.Keys) {
        Test-DatabaseSecretFile -Path $paths[$name]
        $value = Get-Content -LiteralPath $paths[$name] -Raw
        if ($value -notmatch '^[A-Za-z0-9_-]{43}$') { throw "Database credential file is invalid: $name" }
        $values[$name] = $value
    }
    return [pscustomobject]@{ Paths = [pscustomobject]$paths; Values = [pscustomobject]$values }
}

function Test-DatabaseSecretFile {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($item.PSIsContainer -or (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) { throw 'Database credential path is unsafe.' }
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = [Security.Principal.SecurityIdentifier]::new([Security.Principal.WellKnownSidType]::LocalSystemSid, $null)
    $acl = Get-Acl -LiteralPath $Path
    if ($acl.GetOwner([Security.Principal.SecurityIdentifier]) -ne $currentSid) { throw 'Database credential owner is invalid.' }
    $rules = @($acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($rule in $rules) {
        $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier])
        if ($rule.IsInherited -or $rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow -or $rule.FileSystemRights -ne [Security.AccessControl.FileSystemRights]::FullControl -or ($sid -ne $currentSid -and $sid -ne $systemSid)) {
            throw 'Database credential ACL is unsafe.'
        }
        if (-not $seen.Add($sid.Value)) { throw 'Database credential ACL is ambiguous.' }
    }
    if (-not $seen.SetEquals([string[]]@($currentSid.Value, $systemSid.Value))) { throw 'Database credential ACL is incomplete.' }
}

function Get-LocalRuntimeTiming {
    [CmdletBinding()]
    param()
    [pscustomobject]@{
        RunnerReadySeconds = 45
        AppReadySeconds = 30
        ShutdownHttpSeconds = 8
        ProcessExitSeconds = 5
        RunnerCleanupSeconds = 6
    }
}

function Get-ChildStopOutcome {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][bool]$HttpSucceeded,
        [Parameter(Mandatory)][bool]$ExitedBeforeDeadline
    )
    if (-not $HttpSucceeded) { return 'ShutdownRejected' }
    if (-not $ExitedBeforeDeadline) { return 'Forced' }
    return 'Stopped'
}

function Invoke-ChildStop {
    [CmdletBinding()]
    param(
        $Process,
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$Token,
        [Parameter(Mandatory)]$Timing,
        [scriptblock]$SendShutdown = {
            param($requestUrl, $requestToken, $timeoutSeconds)
            $client = [Net.Http.HttpClient]::new()
            $client.Timeout = [TimeSpan]::FromSeconds($timeoutSeconds)
            $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, $requestUrl)
            $request.Headers.Add('X-Developer-Dungeon-Runner-Token', $requestToken)
            return $client.Send($request).IsSuccessStatusCode
        }
    )
    if ($null -eq $Process -or $Process.HasExited) { return 'AlreadyStopped' }
    try {
        $httpSucceeded = & $SendShutdown $Url $Token $Timing.ShutdownHttpSeconds
        $exited = $httpSucceeded -and $Process.WaitForExit($Timing.ProcessExitSeconds * 1000)
        $outcome = Get-ChildStopOutcome -HttpSucceeded $httpSucceeded -ExitedBeforeDeadline $exited
        if ($outcome -ne 'Stopped' -and -not $Process.HasExited) { $Process.Kill($true) }
        return $outcome
    } catch {
        if (-not $Process.HasExited) { $Process.Kill($true) }
        return 'ShutdownFailed'
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

Export-ModuleMember -Function Get-DockerExecutable, Resolve-RequiredJavaHome, Enter-LocalRuntimeLock, Test-MavenWrapperIntegrity, Test-LocalRuntimePrerequisites, Get-ChallengeBuildFingerprint, New-RunnerToken, Initialize-DatabaseSecrets, Get-LocalRuntimeTiming, Get-ChildStopOutcome, Invoke-ChildStop
