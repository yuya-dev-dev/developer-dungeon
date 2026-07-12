[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'lib/LocalRuntime.psm1') -Force
$first = Get-ChallengeBuildFingerprint -RepositoryRoot $root
$second = Get-ChallengeBuildFingerprint -RepositoryRoot $root
if ($first -ne $second -or $first -notmatch '^[0-9a-f]{64}$') { throw 'Build fingerprint is not deterministic.' }
Test-MavenWrapperIntegrity -RepositoryRoot $root
Write-Host 'Local runtime contract checks passed.'
