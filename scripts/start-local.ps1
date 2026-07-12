[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'lib/LocalRuntime.psm1') -Force
Test-LocalRuntimePrerequisites -RequireJava
Test-MavenWrapperIntegrity -RepositoryRoot $root
$dockerExecutable = Get-DockerExecutable

$runtime = Join-Path $root '.developer-dungeon/runtime'
$imageIdPath = Join-Path $runtime 'challenge-image.id'
if (-not (Test-Path -LiteralPath $imageIdPath)) { throw 'Run scripts/build-challenge-image.ps1 first.' }
$imageArtifact = Get-Content -LiteralPath $imageIdPath -Raw
if ($imageArtifact -notmatch '^sha256:[0-9a-f]{64}\n$' -or $imageArtifact.Length -ne 72) { throw 'Challenge image ID artifact is invalid.' }
$imageId = $imageArtifact.Substring(0, 71)
$fingerprint = Get-ChallengeBuildFingerprint -RepositoryRoot $root
$imageMetadata = (& $dockerExecutable image inspect --format '{{.Id}}|{{.Os}}/{{.Architecture}}|{{ index .Config.Labels "io.developer-dungeon.challenge.build-input-sha256" }}' $imageId).Trim()
if ($imageMetadata -ne "$imageId|linux/amd64|$fingerprint") { throw 'Challenge image is stale or invalid. Rebuild it before starting.' }
$gitVersion = (& $dockerExecutable run --rm --platform linux/amd64 --entrypoint /usr/bin/git $imageId --version).Trim()
if ($LASTEXITCODE -ne 0 -or $gitVersion -ne 'git version 2.52.0') { throw 'Challenge image Git version is invalid. Rebuild it before starting.' }

$runnerJar = Get-ChildItem (Join-Path $root 'git-runner/target/git-runner-*.jar') | Where-Object Name -notmatch 'original' | Select-Object -First 1
$appJar = Get-ChildItem (Join-Path $root 'app/target/app-*.jar') | Where-Object Name -notmatch 'original' | Select-Object -First 1
if (-not $runnerJar -or -not $appJar) { throw 'Build the application with .\mvnw.cmd package first.' }
$runtimeTemp = Join-Path $runtime 'tmp'
New-Item -ItemType Directory -Force -Path $runtimeTemp | Out-Null

$bytes = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$token = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
if ($token -notmatch '^[A-Za-z0-9_-]{43}$') { throw 'Failed to generate Runner token.' }

function Start-JavaChild([IO.FileInfo]$jar, [hashtable]$variables) {
    $psi = [Diagnostics.ProcessStartInfo]::new((Join-Path $env:JAVA_HOME 'bin/java.exe'))
    $psi.UseShellExecute = $false
    $psi.ArgumentList.Add("-Djava.io.tmpdir=$runtimeTemp"); $psi.ArgumentList.Add('-jar'); $psi.ArgumentList.Add($jar.FullName)
    $psi.Environment.Clear()
    $psi.Environment['PATH'] = "$env:JAVA_HOME\bin;$env:SystemRoot\System32"
    $psi.Environment['JAVA_HOME'] = $env:JAVA_HOME
    $psi.Environment['SystemRoot'] = $env:SystemRoot
    $psi.Environment['WINDIR'] = $env:SystemRoot
    $psi.Environment['TEMP'] = $runtimeTemp
    $psi.Environment['TMP'] = $runtimeTemp
    foreach ($key in $variables.Keys) { $psi.Environment[$key] = $variables[$key] }
    return [Diagnostics.Process]::Start($psi)
}
function Invoke-Ready([string]$url) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(15)
    do {
        try {
            $client = [Net.Http.HttpClient]::new(); $client.Timeout = [TimeSpan]::FromSeconds(1)
            $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $url)
            $request.Headers.Add('X-Developer-Dungeon-Runner-Token', $token)
            if ($client.Send($request).IsSuccessStatusCode) { return $true }
        } catch { Start-Sleep -Milliseconds 200 }
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    return $false
}
function Stop-Child($process, [string]$url) {
    if ($null -eq $process -or $process.HasExited) { return }
    try {
        $client = [Net.Http.HttpClient]::new(); $client.Timeout = [TimeSpan]::FromSeconds(3)
        $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, $url)
        $request.Headers.Add('X-Developer-Dungeon-Runner-Token', $token)
        $null = $client.Send($request)
        if (-not $process.WaitForExit(5000)) { $process.Kill($true) }
    } catch { if (-not $process.HasExited) { $process.Kill($true) } }
}

$runner = $null; $app = $null
try {
    $runner = Start-JavaChild $runnerJar @{ DEVELOPER_DUNGEON_RUNNER_TOKEN=$token; DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID=$imageId; DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT=$fingerprint; DEVELOPER_DUNGEON_DOCKER_EXECUTABLE=$dockerExecutable }
    if (-not (Invoke-Ready 'http://127.0.0.1:18081/internal/health')) { throw 'Runner readiness failed.' }
    $app = Start-JavaChild $appJar @{ DEVELOPER_DUNGEON_RUNNER_TOKEN=$token; DEVELOPER_DUNGEON_RUNNER_URL='http://127.0.0.1:18081' }
    if (-not (Invoke-Ready 'http://127.0.0.1:8080/internal/health')) { throw 'App readiness failed.' }
    Write-Host 'Developer Dungeon is running at http://127.0.0.1:8080'
    $app.WaitForExit()
} finally {
    Stop-Child $app 'http://127.0.0.1:8080/internal/shutdown'
    Stop-Child $runner 'http://127.0.0.1:18081/internal/shutdown'
}
