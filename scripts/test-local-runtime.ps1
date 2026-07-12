[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'lib/LocalRuntime.psm1') -Force
$first = Get-ChallengeBuildFingerprint -RepositoryRoot $root
$second = Get-ChallengeBuildFingerprint -RepositoryRoot $root
if ($first -ne $second -or $first -notmatch '^[0-9a-f]{64}$') { throw 'Build fingerprint is not deterministic.' }
Test-MavenWrapperIntegrity -RepositoryRoot $root
$tokens = 1..8 | ForEach-Object { New-RunnerToken }
if (@($tokens | Sort-Object -Unique).Count -ne $tokens.Count -or @($tokens | Where-Object { $_ -notmatch '^[A-Za-z0-9_-]{43}$' }).Count -ne 0) { throw 'Runner token generation contract failed.' }
$timing = Get-LocalRuntimeTiming
if ($timing.RunnerReadySeconds -ne 45 -or $timing.AppReadySeconds -gt $timing.RunnerReadySeconds -or $timing.ShutdownHttpSeconds -ne 8 -or $timing.ProcessExitSeconds -ne 5 -or $timing.RunnerCleanupSeconds -ne 6) {
    throw 'Local runtime timing contract failed.'
}
$normalStop = Get-ChildStopOutcome -HttpSucceeded $true -ExitedBeforeDeadline $true
$rejectedStop = Get-ChildStopOutcome -HttpSucceeded $false -ExitedBeforeDeadline $false
$forcedStop = Get-ChildStopOutcome -HttpSucceeded $true -ExitedBeforeDeadline $false
if ($normalStop -ne 'Stopped' -or $rejectedStop -ne 'ShutdownRejected' -or $forcedStop -ne 'Forced') {
    throw 'Local runtime shutdown outcome contract failed.'
}
function New-FakeChild([bool]$exitOnWait) {
    $child = [pscustomobject]@{ HasExited = $false; ExitOnWait = $exitOnWait; KillCount = 0 }
    $child | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value {
        param($milliseconds)
        if ($this.ExitOnWait) { $this.HasExited = $true }
        return $this.ExitOnWait
    }
    $child | Add-Member -MemberType ScriptMethod -Name Kill -Value {
        param($entireTree)
        $this.KillCount++
        $this.HasExited = $true
    }
    return $child
}
$normalChild = New-FakeChild $true
$normalResult = Invoke-ChildStop -Process $normalChild -Url 'http://127.0.0.1/' -Token 'test-token' -Timing $timing -SendShutdown { $true }
$rejectedChild = New-FakeChild $false
$rejectedResult = Invoke-ChildStop -Process $rejectedChild -Url 'http://127.0.0.1/' -Token 'test-token' -Timing $timing -SendShutdown { $false }
$forcedChild = New-FakeChild $false
$forcedResult = Invoke-ChildStop -Process $forcedChild -Url 'http://127.0.0.1/' -Token 'test-token' -Timing $timing -SendShutdown { $true }
$failedChild = New-FakeChild $false
$failedResult = Invoke-ChildStop -Process $failedChild -Url 'http://127.0.0.1/' -Token 'test-token' -Timing $timing -SendShutdown { throw 'timeout' }
$normalChildPassed = $normalResult -eq 'Stopped' -and $normalChild.KillCount -eq 0
$rejectedChildPassed = $rejectedResult -eq 'ShutdownRejected' -and $rejectedChild.KillCount -eq 1
$forcedChildPassed = $forcedResult -eq 'Forced' -and $forcedChild.KillCount -eq 1
$failedChildPassed = $failedResult -eq 'ShutdownFailed' -and $failedChild.KillCount -eq 1
if (-not $normalChildPassed -or -not $rejectedChildPassed -or -not $forcedChildPassed -or -not $failedChildPassed) {
    throw 'Local runtime child shutdown orchestration failed.'
}
Write-Host 'Local runtime contract checks passed.'
