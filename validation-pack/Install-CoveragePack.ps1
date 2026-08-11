param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('medium', 'large', 'stress')]
    [string] $Profile,

    [ValidateSet('client', 'server')]
    [string] $Side = 'client'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$coverageRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'run\coverage'))
$targetRoot = [IO.Path]::GetFullPath((Join-Path $coverageRoot "$Profile-$Side"))
if (-not $targetRoot.StartsWith($coverageRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Coverage target escaped the expected root: $targetRoot"
}

$manifest = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'coverage-packs.json') |
    ConvertFrom-Json
$pack = $manifest.profiles.$Profile
if ($null -eq $pack) {
    throw "Unknown coverage profile: $Profile"
}

$cacheRoot = Join-Path $projectRoot 'run\validation-pack-cache\coverage'
$packCache = Join-Path $cacheRoot 'mrpacks'
$fileCache = Join-Path $cacheRoot 'files'
New-Item -ItemType Directory -Force -Path $packCache, $fileCache, $targetRoot | Out-Null

function Get-Sha512([string] $Path) {
    return (Get-FileHash -Algorithm SHA512 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-VerifiedDownload(
    [string] $Url,
    [string] $ExpectedSha512,
    [string] $Target
) {
    if ((Test-Path -LiteralPath $Target) -and
            (Get-Sha512 $Target) -eq $ExpectedSha512.ToLowerInvariant()) {
        return
    }
    $temporary = "$Target.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -Uri $Url -OutFile $temporary -Headers @{
        'User-Agent' = 'Detective-validation/0.6.1 (local coverage closure)'
    }
    $actual = Get-Sha512 $temporary
    if ($actual -ne $ExpectedSha512.ToLowerInvariant()) {
        Remove-Item -LiteralPath $temporary -Force
        throw "SHA-512 mismatch for $Url"
    }
    Move-Item -LiteralPath $temporary -Destination $Target -Force
}

$mrpack = Join-Path $packCache $pack.mrpackFile
Get-VerifiedDownload $pack.mrpackUrl $pack.mrpackSha512 $mrpack

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($mrpack)
try {
    $indexEntry = $archive.GetEntry('modrinth.index.json')
    if ($null -eq $indexEntry) {
        throw 'The mrpack does not contain modrinth.index.json'
    }
    $reader = [IO.StreamReader]::new($indexEntry.Open())
    try {
        $index = $reader.ReadToEnd() | ConvertFrom-Json
    } finally {
        $reader.Dispose()
    }

    if ($index.dependencies.minecraft -ne $manifest.minecraft) {
        throw "Pack targets Minecraft $($index.dependencies.minecraft), expected $($manifest.minecraft)"
    }

    $selected = @($index.files | Where-Object { $_.env.$Side -ne 'unsupported' })
    foreach ($entry in $selected) {
        $sha = [string] $entry.hashes.sha512
        $cached = Join-Path $fileCache $sha
        Get-VerifiedDownload ([string] $entry.downloads[0]) $sha $cached

        $destination = [IO.Path]::GetFullPath((Join-Path $targetRoot $entry.path))
        if (-not $destination.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw "Pack file escaped the instance root: $($entry.path)"
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        if ((Test-Path -LiteralPath $destination) -and
                (Get-Sha512 $destination) -eq $sha.ToLowerInvariant()) {
            continue
        }
        Remove-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
        try {
            New-Item -ItemType HardLink -Path $destination -Target $cached | Out-Null
        } catch {
            Copy-Item -LiteralPath $cached -Destination $destination
        }
    }

    $overridePrefixes = @('overrides/')
    if ($Side -eq 'client') {
        $overridePrefixes += 'client-overrides/'
    } else {
        $overridePrefixes += 'server-overrides/'
    }
    foreach ($entry in $archive.Entries) {
        $prefix = $overridePrefixes | Where-Object { $entry.FullName.StartsWith($_) } |
            Select-Object -First 1
        if ($null -eq $prefix -or $entry.FullName.EndsWith('/')) {
            continue
        }
        $relative = $entry.FullName.Substring($prefix.Length)
        $destination = [IO.Path]::GetFullPath((Join-Path $targetRoot $relative))
        if (-not $destination.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw "Override escaped the instance root: $($entry.FullName)"
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
    }
} finally {
    $archive.Dispose()
}

$excludedDirectory = Join-Path $targetRoot 'detective-validation-excluded'
if ($Side -eq 'client' -and $null -ne $pack.clientExclusions) {
    New-Item -ItemType Directory -Force -Path $excludedDirectory | Out-Null
    foreach ($exclusion in $pack.clientExclusions) {
        $fileName = [IO.Path]::GetFileName([string] $exclusion.file)
        if ($fileName -ne [string] $exclusion.file) {
            throw "Client exclusion must be a file name: $($exclusion.file)"
        }
        $source = Join-Path (Join-Path $targetRoot 'mods') $fileName
        $destination = Join-Path $excludedDirectory $fileName
        if (Test-Path -LiteralPath $source) {
            Move-Item -LiteralPath $source -Destination $destination -Force
        }
    }
}

$installedJars = @(Get-ChildItem -LiteralPath (Join-Path $targetRoot 'mods') -Filter *.jar -File -ErrorAction SilentlyContinue)
$expectedJars = if ($Side -eq 'client') { [int] $pack.expectedClientJars } else { $null }
if ($Side -eq 'client' -and $installedJars.Count -ne $expectedJars) {
    throw "Installed $($installedJars.Count) client JARs, expected $expectedJars"
}

$resultDirectory = Join-Path $targetRoot 'detective-validation'
New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
[ordered]@{
    schemaVersion = 1
    profile = $Profile
    side = $Side
    project = $pack.project
    projectId = $pack.projectId
    version = $pack.version
    versionId = $pack.versionId
    minecraft = $manifest.minecraft
    declaredNeoForge = $pack.declaredNeoForge
    validationRuntimeNeoForge = $manifest.runtimeNeoForge
    installedJarCount = $installedJars.Count
    generatedAt = [DateTimeOffset]::UtcNow.ToString('O')
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $resultDirectory 'coverage-pack.json') -Encoding utf8

Write-Output "Installed $Profile/${Side}: $($installedJars.Count) JARs in $targetRoot"
