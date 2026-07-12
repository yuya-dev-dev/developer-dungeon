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
$migratorJar = Get-ChildItem (Join-Path $root 'db-migrator/target/db-migrator-*.jar') | Where-Object Name -notmatch 'original' | Select-Object -First 1
$appJar = Get-ChildItem (Join-Path $root 'app/target/app-*.jar') | Where-Object Name -notmatch 'original' | Select-Object -First 1
if (-not $runnerJar -or -not $migratorJar -or -not $appJar) { throw 'Build the application with .\mvnw.cmd package first.' }
$runtimeTemp = Join-Path $runtime 'tmp'
New-Item -ItemType Directory -Force -Path $runtimeTemp | Out-Null
$ledgerPath = Join-Path $runtime 'runner-owned-containers.json'
$timing = Get-LocalRuntimeTiming

$token = New-RunnerToken
$database = Initialize-DatabaseSecrets -RuntimeDirectory $runtime -DockerExecutable $dockerExecutable
$databaseUrl = 'jdbc:postgresql://127.0.0.1:15432/developer_dungeon?currentSchema=developer_dungeon'
$env:DEVELOPER_DUNGEON_DB_ADMIN_PASSWORD_FILE = $database.Paths.Admin
$env:DEVELOPER_DUNGEON_DB_MIGRATOR_PASSWORD_FILE = $database.Paths.Migrator
$env:DEVELOPER_DUNGEON_DB_APP_PASSWORD_FILE = $database.Paths.App

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
function Invoke-Ready([string]$url, [int]$seconds) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($seconds)
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
    return Invoke-ChildStop -Process $process -Url $url -Token $token -Timing $timing
}
function Assert-DatabaseOwnership {
    $volumeOutput = ((& $dockerExecutable volume ls --quiet --filter 'name=^developer-dungeon-postgres-data$' | Out-String).Trim())
    $volumeNames = @($volumeOutput.Split([Environment]::NewLine, [StringSplitOptions]::RemoveEmptyEntries))
    if ($LASTEXITCODE -ne 0) { throw 'Management database volume listing failed.' }
    if ($volumeNames.Count -gt 1 -or ($volumeNames.Count -eq 1 -and $volumeNames[0] -ne 'developer-dungeon-postgres-data')) { throw 'Management database volume identity is invalid.' }
    if ($volumeNames.Count -eq 1) {
        $volumeLabels = (& $dockerExecutable volume inspect --format '{{json .Labels}}' developer-dungeon-postgres-data).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Management database volume inspection failed.' }
        if ($volumeLabels -notlike '*"io.developer-dungeon.project":"developer-dungeon"*' -or $volumeLabels -notlike '*"io.developer-dungeon.owner":"local-runtime"*') { throw 'Management database volume ownership verification failed.' }
    }
    $containerOutput = ((& $dockerExecutable ps --all --quiet --filter 'label=com.docker.compose.project=developer-dungeon' --filter 'label=com.docker.compose.service=postgres' | Out-String).Trim())
    $ids = @($containerOutput.Split([Environment]::NewLine, [StringSplitOptions]::RemoveEmptyEntries))
    if ($LASTEXITCODE -ne 0) { throw 'Management database container listing failed.' }
    foreach ($id in $ids) {
        if ($id -notmatch '^[0-9a-f]{12,64}$') { throw 'Management database container identity is invalid.' }
        $labels = (& $dockerExecutable container inspect --format '{{json .Config.Labels}}' $id).Trim()
        if ($LASTEXITCODE -ne 0 -or $labels -notlike '*"com.docker.compose.project":"developer-dungeon"*' -or $labels -notlike '*"com.docker.compose.service":"postgres"*' -or $labels -notlike '*"io.developer-dungeon.owner":"local-runtime"*') {
            throw 'Management database container ownership verification failed.'
        }
    }
}
function Start-Database {
    Assert-DatabaseOwnership
    & $dockerExecutable compose --project-name developer-dungeon --file (Join-Path $root 'compose.yaml') up --detach --wait postgres
    if ($LASTEXITCODE -ne 0) { throw 'Management database readiness failed.' }
    $id = (& $dockerExecutable compose --project-name developer-dungeon --file (Join-Path $root 'compose.yaml') ps --quiet postgres).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Management database identity lookup failed.' }
    if ($id -notmatch '^[0-9a-f]{12,64}$') { throw 'Management database identity is invalid.' }
    $labels = (& $dockerExecutable container inspect --format '{{json .Config.Labels}}' $id).Trim()
    if ($LASTEXITCODE -ne 0 -or $labels -notlike '*"com.docker.compose.project":"developer-dungeon"*' -or $labels -notlike '*"com.docker.compose.service":"postgres"*' -or $labels -notlike '*"io.developer-dungeon.owner":"local-runtime"*') {
        throw 'Management database ownership verification failed.'
    }
}
function Stop-Database {
    Assert-DatabaseOwnership
    $id = (& $dockerExecutable compose --project-name developer-dungeon --file (Join-Path $root 'compose.yaml') ps --quiet postgres).Trim()
    if ($LASTEXITCODE -ne 0) { return 'DatabaseIdentityLookupFailed' }
    if (-not $id) { return 'AlreadyStopped' }
    if ($id -notmatch '^[0-9a-f]{12,64}$') { return 'DatabaseIdentityInvalid' }
    $labels = (& $dockerExecutable container inspect --format '{{json .Config.Labels}}' $id).Trim()
    if ($LASTEXITCODE -ne 0 -or $labels -notlike '*"com.docker.compose.project":"developer-dungeon"*' -or $labels -notlike '*"com.docker.compose.service":"postgres"*') { return 'DatabaseOwnershipInvalid' }
    & $dockerExecutable compose --project-name developer-dungeon --file (Join-Path $root 'compose.yaml') stop postgres
    if ($LASTEXITCODE -ne 0) { return 'DatabaseStopFailed' }
    return 'Stopped'
}

$runner = $null; $app = $null; $databaseStarted = $false
try {
    Start-Database
    $databaseStarted = $true
    $migrator = Start-JavaChild $migratorJar @{ DEVELOPER_DUNGEON_MIGRATION_DB_URL=$databaseUrl; DEVELOPER_DUNGEON_MIGRATION_DB_USER='developer_dungeon_migrator'; DEVELOPER_DUNGEON_MIGRATION_DB_PASSWORD=$database.Values.Migrator }
    $migrator.WaitForExit()
    if ($migrator.ExitCode -ne 0) { throw 'Management database migration failed.' }
    $runner = Start-JavaChild $runnerJar @{ DEVELOPER_DUNGEON_RUNNER_TOKEN=$token; DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID=$imageId; DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT=$fingerprint; DEVELOPER_DUNGEON_DOCKER_EXECUTABLE=$dockerExecutable; DEVELOPER_DUNGEON_CONTAINER_LEDGER_PATH=$ledgerPath }
    if (-not (Invoke-Ready 'http://127.0.0.1:18081/internal/health' $timing.RunnerReadySeconds)) { throw 'Runner readiness failed.' }
    $app = Start-JavaChild $appJar @{ DEVELOPER_DUNGEON_RUNNER_TOKEN=$token; DEVELOPER_DUNGEON_RUNNER_URL='http://127.0.0.1:18081'; DEVELOPER_DUNGEON_APP_DB_URL=$databaseUrl; DEVELOPER_DUNGEON_APP_DB_USER='developer_dungeon_app'; DEVELOPER_DUNGEON_APP_DB_PASSWORD=$database.Values.App }
    if (-not (Invoke-Ready 'http://127.0.0.1:8080/internal/health' $timing.AppReadySeconds)) { throw 'App readiness failed.' }
    Write-Host 'Developer Dungeon is running at http://127.0.0.1:8080'
    $app.WaitForExit()
} finally {
    $appStop = Stop-Child $app 'http://127.0.0.1:8080/internal/shutdown'
    $runnerStop = Stop-Child $runner 'http://127.0.0.1:18081/internal/shutdown'
    $databaseStop = if ($databaseStarted) { Stop-Database } else { 'AlreadyStopped' }
    $failedStops = @(@($appStop, $runnerStop, $databaseStop) | Where-Object { $_ -notin @('Stopped', 'AlreadyStopped') })
    if ($failedStops.Count -gt 0) {
        throw "Local shutdown was incomplete ($($failedStops -join ', ')); the next startup must perform recovery."
    }
}
