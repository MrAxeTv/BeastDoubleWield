# Structural checks only; native Minecraft rendering still needs client testing.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = Split-Path -Parent $PSScriptRoot
$exampleRoot = Join-Path $projectRoot 'examples/resourcepack'
$output = Join-Path $projectRoot 'target/resourcepack-test'
$builder = Join-Path $PSScriptRoot 'build-resourcepacks.ps1'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

& $builder -OutputDirectory $output | Out-Null
$before = @{}
foreach ($file in (Get-ChildItem -LiteralPath $output -File)) {
    $before[$file.Name] = @((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash, $file.LastWriteTimeUtc.Ticks)
}
& $builder -OutputDirectory $output | Out-Null
foreach ($file in (Get-ChildItem -LiteralPath $output -File)) {
    Assert-True ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ceq $before[$file.Name][0]) "Changed bytes on repeat build: $($file.Name)"
    Assert-True ($file.LastWriteTimeUtc.Ticks -eq $before[$file.Name][1]) "Unnecessary rewrite on repeat build: $($file.Name)"
}

$manifest = Get-Content -LiteralPath (Join-Path $exampleRoot 'profiles.json') -Raw | ConvertFrom-Json
$records = (Get-Content -LiteralPath (Join-Path $output 'checksums.json') -Raw | ConvertFrom-Json).packs
foreach ($record in $records) {
    $path = Join-Path $output $record.file
    Assert-True ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $record.sha256) "Wrong SHA-256: $($record.file)"
    Assert-True ((Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ceq $record.sha1) "Wrong SHA-1: $($record.file)"
    $archive = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $names = @($archive.Entries.FullName)
        $sorted = [string[]]$names.Clone()
        [Array]::Sort($sorted, [StringComparer]::Ordinal)
        Assert-True (($names -join '|') -ceq ($sorted -join '|')) "Unsorted ZIP entries: $($record.file)"
        $sourceName = $manifest.profiles.($record.profile).source
        $expectedCount = if ($sourceName -eq 'cmd') { 4 } else { 2 }
        Assert-True ($archive.Entries.Count -eq $expectedCount) "Unexpected ZIP assets: $($record.file)"
        foreach ($entry in $archive.Entries) {
            Assert-True ($entry.LastWriteTime.Year -eq 1980 -and $entry.LastWriteTime.Month -eq 1 -and $entry.LastWriteTime.Day -eq 1) "Unstable ZIP timestamp: $($entry.FullName)"
            Assert-True ($entry.Length -eq $entry.CompressedLength) "Expected a stored ZIP entry: $($entry.FullName)"
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $entryText = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $parsed = $entryText | ConvertFrom-Json
            Assert-True (-not $entryText.Contains("`r")) "Unexpected CR newline: $($entry.FullName)"
            if ($entry.FullName -eq 'pack.mcmeta') {
                $expected = $manifest.profiles.($record.profile).pack | ConvertTo-Json -Depth 20 -Compress
                Assert-True (($parsed.pack | ConvertTo-Json -Depth 20 -Compress) -ceq $expected) "Wrong pack metadata: $($record.file)"
            } else {
                $sourcePath = Join-Path (Join-Path $exampleRoot $sourceName) $entry.FullName
                $expected = [IO.File]::ReadAllText($sourcePath).Replace("`r`n", "`n").Replace("`r", "`n").TrimEnd() + "`n"
                Assert-True ($entryText -ceq $expected) "ZIP/source mismatch: $($entry.FullName)"
            }
        }
    } finally {
        $archive.Dispose()
    }
}

$old = Get-Content -LiteralPath (Join-Path $exampleRoot 'cmd/assets/minecraft/models/item/feather.json') -Raw | ConvertFrom-Json
$modern = (Get-Content -LiteralPath (Join-Path $exampleRoot 'item-definitions/assets/minecraft/items/feather.json') -Raw | ConvertFrom-Json).model
foreach ($value in @(0, 1, 35020, 35021, 35022, 35023, 1000000)) {
    $oldModel = 'minecraft:item/feather'
    foreach ($entry in $old.overrides) {
        if ($value -ge $entry.predicate.custom_model_data) { $oldModel = $entry.model }
    }
    $newModel = $modern.fallback
    foreach ($entry in ($modern.entries | Sort-Object threshold)) {
        if ($value -ge $entry.threshold) { $newModel = $entry.model }
    }
    $invisible = $value -eq $manifest.custom_model_data
    Assert-True (($oldModel -eq 'beastdoublewield:item/invisible') -eq $invisible) "Wrong old-model selection for $value"
    Assert-True (($newModel.type -eq 'minecraft:empty') -eq $invisible) "Wrong item-definition selection for $value"
    if (-not $invisible) {
        Assert-True ($newModel.model -eq 'minecraft:item/feather') "Ordinary feather fallback missing for $value"
    }
}
$empty = Get-Content -LiteralPath (Join-Path $exampleRoot 'cmd/assets/beastdoublewield/models/item/invisible.json') -Raw | ConvertFrom-Json
Assert-True ($empty.elements.Count -eq 0 -and $empty.PSObject.Properties.Name -notcontains 'parent') 'Invisible model must not inherit visible geometry'
$normal = Get-Content -LiteralPath (Join-Path $exampleRoot 'cmd/assets/beastdoublewield/models/item/normal_feather.json') -Raw | ConvertFrom-Json
Assert-True ($normal.parent -eq 'minecraft:item/generated' -and $normal.textures.layer0 -eq 'minecraft:item/feather') 'Normal feather texture/model fallback missing'
Assert-True ($normal.PSObject.Properties.Name -notcontains 'overrides') 'Normal feather fallback must not recurse through overrides'
Assert-True (($normal.display | ConvertTo-Json -Depth 20 -Compress) -ceq ($old.display | ConvertTo-Json -Depth 20 -Compress)) 'Normal feather display transform differs'
Write-Output "PASS: $($records.Count) pack profiles, repeat-build hashes/timestamps, ZIP metadata/assets, and selector boundaries. Native rendering was not tested."
