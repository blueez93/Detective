param(
    [ValidateSet('medium')]
    [string] $Profile = 'medium',
    [string] $SourceProfile = 'large'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$coverageRoot = Join-Path $repositoryRoot 'run\coverage'
$sourceRoot = Join-Path $coverageRoot "$SourceProfile-client"
$targetRoot = Join-Path $coverageRoot "$Profile-client"

if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "Source coverage profile is missing: $sourceRoot"
}
if (Test-Path -LiteralPath $targetRoot) {
    throw "Target coverage profile already exists: $targetRoot"
}

$mediumFiles = @(
    'AI-Improvements-1.21-0.5.3.jar',
    'alltheleaks-1.1.11+1.21.1-neoforge.jar',
    'appleskin-neoforge-mc1.21-3.0.9.jar',
    'architectury-13.0.11-neoforge.jar',
    'BadOptimizations-2.4.1-1.21.1.jar',
    'balm-neoforge-1.21.1-21.0.64.jar',
    'better_modlist-21.1.0.jar',
    'c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar',
    'cloth-config-15.0.140-neoforge.jar',
    'collective-1.21.1-8.39.jar',
    'Controlling-neoforge-1.21.1-19.0.5.jar',
    'Corgilib-NeoForge-1.21.1-5.0.0.9.jar',
    'entityculling-neoforge-1.10.5-mc1.21.1.jar',
    'ferritecore-7.0.3-neoforge.jar',
    'friendsandfoes-neoforge-4.0.26+mc1.21.1.jar',
    'guideme-21.1.17.jar',
    'ImmediatelyFast-NeoForge-1.6.11+1.21.1.jar',
    'iris-neoforge-1.8.14-beta.1+mc1.21.1.jar',
    'ironchest-1.21-neoforge-16.0.7.jar',
    'Jade-1.21.1-NeoForge-15.10.5.jar',
    'jei-1.21.1-neoforge-19.43.0.392.jar',
    'krypton_fnp-neoforge-1.21.1-0.2.28.1-1.21.1.jar',
    'Ksyxis-1.4.3.jar',
    'lighty-neoforge-3.0.0-beta.8+1.21.1.jar',
    'lithium-neoforge-0.15.4+mc1.21.1.jar',
    'lithostitched-1.7.13-neoforge-21.1.jar',
    'lootr-neoforge-1.21.1-1.11.37.122.jar',
    'Mekanism-1.21.1-10.7.19.85.jar',
    'MekanismGenerators-1.21.1-10.7.19.85.jar',
    'MouseTweaks-neoforge-mc1.21-2.26.1.jar',
    'Oh-The-Biomes-Weve-Gone-NeoForge-2.6.0.jar',
    'Oh-The-Trees-Youll-Grow-neoforge-1.21.1-5.3.2.jar',
    'packetfixer-3.3.1-1.20.5-1.21.X-merged.jar',
    'reeses-sodium-options-neoforge-2.2.3+mc1.21.1.jar',
    'resourcefulconfig-neoforge-1.21-3.0.11.jar',
    'resourcefullib-neoforge-1.21-3.0.12.jar',
    'saturn-mc1.21.1-0.1.5.jar',
    'Searchables-neoforge-1.21.1-1.0.2.jar',
    'servercore-neoforge-1.5.19+1.21.1.jar',
    'sodium-extra-neoforge-0.9.3+mc1.21.1.jar',
    'sodium-neoforge-0.8.13-beta.1+mc1.21.1.jar',
    'sophisticatedbackpacks-1.21.1-3.25.73.2020.jar',
    'sophisticatedcore-1.21.1-1.4.80.2194.jar',
    'tectonic-3.0.26-neoforge-21.1.jar',
    'TerraBlender-neoforge-1.21.1-4.1.0.8.jar',
    'waystones-neoforge-1.21.1-21.1.39.jar',
    'xaerominimap-neoforge-1.21.1-26.4.2.jar',
    'xaeroworldmap-neoforge-1.21.1-1.44.2.jar',
    'yet_another_config_lib_v3-3.8.2+1.21.1-neoforge.jar'
)

New-Item -ItemType Directory -Force -Path (Join-Path $targetRoot 'mods') | Out-Null
foreach ($fileName in $mediumFiles) {
    $source = Join-Path (Join-Path $sourceRoot 'mods') $fileName
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Required source mod is missing: $fileName"
    }
    $destination = Join-Path (Join-Path $targetRoot 'mods') $fileName
    try {
        New-Item -ItemType HardLink -Path $destination -Target $source | Out-Null
    } catch {
        Copy-Item -LiteralPath $source -Destination $destination
    }
}

foreach ($directoryName in @('config', 'defaultconfigs', 'resourcepacks')) {
    $sourceDirectory = Join-Path $sourceRoot $directoryName
    if (Test-Path -LiteralPath $sourceDirectory -PathType Container) {
        Copy-Item -LiteralPath $sourceDirectory -Destination (Join-Path $targetRoot $directoryName) -Recurse
    }
}

$validationDirectory = Join-Path $targetRoot 'detective-validation'
New-Item -ItemType Directory -Force -Path $validationDirectory | Out-Null
[ordered]@{
    schemaVersion = 1
    profile = $Profile
    sourceProfile = $SourceProfile
    sourceProject = 'Technical Electrical: Striking Surprise 6.2.6'
    minecraft = '1.21.1'
    validationRuntimeNeoForge = '21.1.248'
    installedJarCount = $mediumFiles.Count
    selection = 'Exact native-NeoForge subset of the validated large pack; no third-party files are redistributed in Detective.'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $validationDirectory 'coverage-pack.json') -Encoding utf8

Write-Output "Created $Profile from $SourceProfile: $($mediumFiles.Count) JARs in $targetRoot"
