[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'lib/LocalRuntime.psm1') -Force
Test-LocalRuntimePrerequisites
Test-MavenWrapperIntegrity -RepositoryRoot $root
$dockerExecutable = Get-DockerExecutable
$fingerprint = Get-ChallengeBuildFingerprint -RepositoryRoot $root
$image = 'developer-dungeon/git-challenge:0.1.0'
& $dockerExecutable build --platform linux/amd64 --pull=false --label 'io.developer-dungeon.project=developer-dungeon' --label "io.developer-dungeon.challenge.build-input-sha256=$fingerprint" --tag $image (Join-Path $root 'challenge-image')
if ($LASTEXITCODE -ne 0) { throw 'Challenge image build failed.' }
$id = (& $dockerExecutable image inspect --format '{{.Id}}' $image).Trim()
if ($id -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Challenge image ID is invalid.' }
$metadata = (& $dockerExecutable image inspect --format '{{.Os}}/{{.Architecture}}|{{ index .Config.Labels "io.developer-dungeon.project" }}|{{ index .Config.Labels "io.developer-dungeon.challenge.build-input-sha256" }}' $id).Trim()
if ($metadata -ne "linux/amd64|developer-dungeon|$fingerprint") { throw 'Challenge image metadata verification failed.' }
$git = (& $dockerExecutable run --rm --platform linux/amd64 --entrypoint /usr/bin/git $id --version).Trim()
if ($git -ne 'git version 2.52.0') { throw "Unexpected Git version: $git" }
$runtime = Join-Path $root '.developer-dungeon/runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$temp = Join-Path $runtime 'challenge-image.id.tmp'
[IO.File]::WriteAllText($temp, "$id`n", [Text.UTF8Encoding]::new($false))
Move-Item -Force $temp (Join-Path $runtime 'challenge-image.id')
Write-Host "Challenge image ready: $id"
