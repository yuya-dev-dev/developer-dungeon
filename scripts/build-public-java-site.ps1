[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$outputRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot ".developer-dungeon/public-java-pages"))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$repositoryRootItem = Get-Item -LiteralPath $repositoryRoot -Force
if (($repositoryRootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or $null -ne $repositoryRootItem.LinkType) {
    throw "Repository root must not be a reparse point or symbolic link: $repositoryRoot"
}

function Assert-RepositoryPathWithoutReparsePoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [bool]$MustExist = $true
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if ($fullPath -ne $repositoryRoot -and -not $fullPath.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path must remain inside the repository: $fullPath"
    }

    $relativePath = [System.IO.Path]::GetRelativePath($repositoryRoot, $fullPath)
    $currentPath = $repositoryRoot
    if ($relativePath -ne ".") {
        foreach ($segment in $relativePath -split '[\\/]') {
            $currentPath = Join-Path $currentPath $segment
            if (-not (Test-Path -LiteralPath $currentPath)) {
                if ($MustExist) {
                    throw "Required repository path does not exist: $currentPath"
                }
                break
            }
            $item = Get-Item -LiteralPath $currentPath -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or $null -ne $item.LinkType) {
                throw "Repository path must not contain a reparse point or symbolic link: $currentPath"
            }
        }
    }

    return $fullPath
}

if ($outputRoot -eq $repositoryRoot -or -not $outputRoot.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The fixed public output directory must resolve inside the repository and must not be the repository root."
}

[void](Assert-RepositoryPathWithoutReparsePoint -Path (Split-Path -Parent $outputRoot) -MustExist $false)

if (Test-Path -LiteralPath $outputRoot) {
    [void](Assert-RepositoryPathWithoutReparsePoint -Path $outputRoot)
    Remove-Item -LiteralPath $outputRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outputRoot "assets") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outputRoot "data") -Force | Out-Null

$publicSource = Join-Path $repositoryRoot "public-java"
$problemSource = Join-Path $repositoryRoot "app/src/main/resources/java-problems"
$baseCss = Join-Path $repositoryRoot "app/src/main/resources/static/java-learning.css"
$catalogPath = Join-Path $problemSource "catalog.json"
$slugPattern = '^[a-z0-9]+(?:-[a-z0-9]+)*$'
$referencePattern = '^[A-Z][A-Za-z0-9]*\.java$'

foreach ($file in @("index.html", "problems.html", "problem.html")) {
    $sourcePath = Join-Path $publicSource $file
    [void](Assert-RepositoryPathWithoutReparsePoint -Path $sourcePath)
    Copy-Item -LiteralPath $sourcePath -Destination (Join-Path $outputRoot $file)
}
$publicJavaScript = Join-Path $publicSource "public-java.js"
$publicOverridesCss = Join-Path $publicSource "public-overrides.css"
foreach ($sourcePath in @($publicJavaScript, $publicOverridesCss, $baseCss, $catalogPath)) {
    [void](Assert-RepositoryPathWithoutReparsePoint -Path $sourcePath)
}
Copy-Item -LiteralPath $publicJavaScript -Destination (Join-Path $outputRoot "assets/public-java.js")

$cssText = [System.IO.File]::ReadAllText($baseCss) + [Environment]::NewLine + [System.IO.File]::ReadAllText($publicOverridesCss)
[System.IO.File]::WriteAllText((Join-Path $outputRoot "assets/public-java.css"), $cssText, [System.Text.UTF8Encoding]::new($false))

$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($null -eq $catalog.directories -or $catalog.directories.Count -ne 9) {
    throw "The public Java catalog must contain exactly 9 problem directories."
}

$directories = @($catalog.directories)
if (($directories | Select-Object -Unique).Count -ne $directories.Count) {
    throw "The public Java catalog contains duplicate problem directories."
}

$expectedFiles = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($path in @("index.html", "problems.html", "problem.html", "assets/public-java.css", "assets/public-java.js", "data/catalog.json", ".nojekyll")) {
    [void]$expectedFiles.Add($path)
}

Copy-Item -LiteralPath $catalogPath -Destination (Join-Path $outputRoot "data/catalog.json")

foreach ($slugValue in $directories) {
    $slug = [string]$slugValue
    if ($slug -notmatch $slugPattern) {
        throw "Invalid public problem slug: $slug"
    }

    $sourceDirectory = Join-Path $problemSource $slug
    $problemPath = Join-Path $sourceDirectory "problem.json"
    [void](Assert-RepositoryPathWithoutReparsePoint -Path $problemPath)
    $problem = Get-Content -LiteralPath $problemPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$problem.slug -ne $slug) {
        throw "Problem slug does not match its catalog directory: $slug"
    }
    if (@("BEGINNER", "INTERMEDIATE", "ADVANCED") -notcontains [string]$problem.difficulty) {
        throw "Problem difficulty is not public-safe: $slug"
    }
    if ($null -eq $problem.referenceFiles -or $problem.referenceFiles.Count -lt 1) {
        throw "Problem must contain at least one reference file: $slug"
    }

    $destinationDirectory = Join-Path $outputRoot "data/$slug"
    $referenceDestination = Join-Path $destinationDirectory "reference"
    New-Item -ItemType Directory -Path $referenceDestination -Force | Out-Null
    Copy-Item -LiteralPath $problemPath -Destination (Join-Path $destinationDirectory "problem.json")
    [void]$expectedFiles.Add("data/$slug/problem.json")

    foreach ($referenceValue in @($problem.referenceFiles)) {
        $referenceFile = [string]$referenceValue
        if ($referenceFile -notmatch $referencePattern) {
            throw "Invalid reference file name for ${slug}: $referenceFile"
        }
        $referencePath = Join-Path $sourceDirectory "reference/$referenceFile"
        if (-not (Test-Path -LiteralPath $referencePath -PathType Leaf)) {
            throw "Missing reference file for ${slug}: $referenceFile"
        }
        [void](Assert-RepositoryPathWithoutReparsePoint -Path $referencePath)
        Copy-Item -LiteralPath $referencePath -Destination (Join-Path $referenceDestination $referenceFile)
        [void]$expectedFiles.Add("data/$slug/reference/$referenceFile")
    }
}

[System.IO.File]::WriteAllText((Join-Path $outputRoot ".nojekyll"), "", [System.Text.UTF8Encoding]::new($false))

$actualFiles = Get-ChildItem -LiteralPath $outputRoot -File -Recurse -Force | ForEach-Object {
    if (($_.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "The generated site contains a reparse point: $($_.FullName)"
    }
    $_.FullName.Substring($outputRoot.Length).TrimStart('\', '/') -replace '\\', '/'
}

$unexpected = @($actualFiles | Where-Object { -not $expectedFiles.Contains($_) })
$missing = @($expectedFiles | Where-Object { $actualFiles -notcontains $_ })
if ($unexpected.Count -gt 0 -or $missing.Count -gt 0) {
    throw "Generated artifact differs from the allowlist. Unexpected=[$($unexpected -join ', ')] Missing=[$($missing -join ', ')]"
}

$forbiddenExtensions = @(".class", ".jar", ".properties", ".sql", ".db", ".env", ".yml", ".yaml")
$forbidden = @($actualFiles | Where-Object { $forbiddenExtensions -contains [System.IO.Path]::GetExtension($_).ToLowerInvariant() })
if ($forbidden.Count -gt 0) {
    throw "Generated artifact contains forbidden files: $($forbidden -join ', ')"
}

Write-Host "Public Java site built successfully: $outputRoot"
Write-Host "Allowlisted files: $($actualFiles.Count)"
