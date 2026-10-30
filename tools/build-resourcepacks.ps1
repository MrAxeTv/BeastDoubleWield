param(
    [string[]]$Profile = @(),
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'target/resourcepacks')
)

# Build-only tool: never installs packs, changes enabled packs, or starts a server.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$examplesRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'examples/resourcepack'))
$manifest = Get-Content -LiteralPath (Join-Path $examplesRoot 'profiles.json') -Raw | ConvertFrom-Json
$knownProfiles = @($manifest.profiles.PSObject.Properties.Name)
$selected = if ($Profile.Count -eq 0) { $knownProfiles } else { $Profile }
foreach ($name in $selected) {
    if ($knownProfiles -notcontains $name -or $name -notmatch '^[a-z0-9._-]+$') {
        throw "Unknown or invalid resource-pack profile: $name"
    }
}
$destination = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($destination) | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Get-Crc32([byte[]]$Content) {
    [uint32]$crc = [uint32]::MaxValue
    foreach ($value in $Content) {
        $crc = $crc -bxor $value
        for ($bit = 0; $bit -lt 8; $bit++) {
            if (($crc -band 1) -ne 0) {
                $crc = ($crc -shr 1) -bxor [uint32]3988292384
            } else {
                $crc = $crc -shr 1
            }
        }
    }
    return $crc -bxor [uint32]::MaxValue
}

function New-StoredZip([hashtable]$Contents) {
    # Write the small ZIP32 archive directly. ZipArchive's NoCompression emits
    # different methods on .NET Framework and modern .NET; fixed headers and
    # stored bytes make this output identical on both PowerShell runtimes.
    $buffer = [IO.MemoryStream]::new()
    $writer = [IO.BinaryWriter]::new($buffer)
    try {
        $names = [string[]]$Contents.Keys
        [Array]::Sort($names, [StringComparer]::Ordinal)
        if ($names.Length -gt 65535) { throw 'Too many resource-pack entries for ZIP32' }
        $entries = @()
        foreach ($entryName in $names) {
            [byte[]]$nameBytes = $utf8.GetBytes($entryName)
            [byte[]]$content = $Contents[$entryName]
            $entry = @{ name = $nameBytes; length = $content.Length; crc = (Get-Crc32 $content); offset = $buffer.Position }
            $entries += $entry
            $writer.Write([uint32]0x04034b50)
            $writer.Write([uint16]20) # required ZIP version
            $writer.Write([uint16]2048) # UTF-8
            $writer.Write([uint16]0) # stored
            $writer.Write([uint16]0) # midnight
            $writer.Write([uint16]33) # 1980-01-01
            $writer.Write([uint32]$entry.crc)
            $writer.Write([uint32]$entry.length)
            $writer.Write([uint32]$entry.length)
            $writer.Write([uint16]$nameBytes.Length)
            $writer.Write([uint16]0)
            $writer.Write($nameBytes)
            $writer.Write($content)
        }
        $directoryOffset = $buffer.Position
        foreach ($entry in $entries) {
            $writer.Write([uint32]0x02014b50)
            $writer.Write([uint16]20) # fixed creator system/version
            $writer.Write([uint16]20)
            $writer.Write([uint16]2048)
            $writer.Write([uint16]0)
            $writer.Write([uint16]0)
            $writer.Write([uint16]33)
            $writer.Write([uint32]$entry.crc)
            $writer.Write([uint32]$entry.length)
            $writer.Write([uint32]$entry.length)
            $writer.Write([uint16]$entry.name.Length)
            $writer.Write([uint16]0) # extra
            $writer.Write([uint16]0) # comment
            $writer.Write([uint16]0) # disk
            $writer.Write([uint16]0) # internal attributes
            $writer.Write([uint32]0) # external attributes
            $writer.Write([uint32]$entry.offset)
            $writer.Write([byte[]]$entry.name)
        }
        $directoryLength = $buffer.Position - $directoryOffset
        $writer.Write([uint32]0x06054b50)
        $writer.Write([uint16]0)
        $writer.Write([uint16]0)
        $writer.Write([uint16]$entries.Count)
        $writer.Write([uint16]$entries.Count)
        $writer.Write([uint32]$directoryLength)
        $writer.Write([uint32]$directoryOffset)
        $writer.Write([uint16]0)
        $writer.Flush()
        return ,$buffer.ToArray()
    } finally {
        $writer.Dispose()
        $buffer.Dispose()
    }
}

function Get-Digest([byte[]]$Content, [string]$Algorithm) {
    $hasher = [Security.Cryptography.HashAlgorithm]::Create($Algorithm)
    try {
        return ([BitConverter]::ToString($hasher.ComputeHash($Content))).Replace('-', '').ToLowerInvariant()
    } finally {
        $hasher.Dispose()
    }
}

function Write-IfChanged([string]$Path, [byte[]]$Content) {
    if ([IO.File]::Exists($Path)) {
        $existing = [IO.File]::ReadAllBytes($Path)
        if ($existing.Length -eq $Content.Length -and
            (Get-Digest $existing 'SHA256') -eq (Get-Digest $Content 'SHA256')) {
            return
        }
    }
    [IO.File]::WriteAllBytes($Path, $Content)
}

$records = @()
foreach ($name in ($selected | Sort-Object -Unique)) {
    $settings = $manifest.profiles.$name
    $source = [IO.Path]::GetFullPath((Join-Path $examplesRoot $settings.source))
    $rootPrefix = $examplesRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $source.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not [IO.Directory]::Exists($source)) {
        throw "Pack source must be a subdirectory of examples/resourcepack: $source"
    }
    $contents = @{}
    foreach ($asset in (Get-ChildItem -LiteralPath $source -Recurse)) {
        if (($asset.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Pack sources cannot contain symbolic links: $($asset.FullName)"
        }
        if ($asset.PSIsContainer) { continue }
        $entryName = $asset.FullName.Substring($source.Length + 1).Replace('\', '/')
        if (-not $entryName.StartsWith('assets/', [StringComparison]::Ordinal) -or $asset.Extension -cne '.json') {
            throw "Unexpected pack asset: $entryName"
        }
        $sourceText = [IO.File]::ReadAllText($asset.FullName).Replace("`r`n", "`n").Replace("`r", "`n").TrimEnd() + "`n"
        $null = $sourceText | ConvertFrom-Json
        $contents[$entryName] = $utf8.GetBytes($sourceText)
    }
    if ($contents.Count -eq 0) { throw "Pack source has no assets: $source" }
    $metadata = [ordered]@{ pack = $settings.pack } | ConvertTo-Json -Depth 20 -Compress
    $contents['pack.mcmeta'] = $utf8.GetBytes($metadata + "`n")
    [byte[]]$bytes = New-StoredZip $contents
    $filename = "BeastDoubleWield-placeholder-$name.zip"
    $sha1 = Get-Digest $bytes 'SHA1'
    $sha256 = Get-Digest $bytes 'SHA256'
    Write-IfChanged (Join-Path $destination $filename) $bytes
    Write-IfChanged (Join-Path $destination ($filename + '.sha1')) ($utf8.GetBytes($sha1 + "`n"))
    Write-IfChanged (Join-Path $destination ($filename + '.sha256')) ($utf8.GetBytes($sha256 + "`n"))
    $records += [ordered]@{ profile = $name; file = $filename; bytes = $bytes.Length; sha1 = $sha1; sha256 = $sha256 }
    Write-Output "${filename}: $($bytes.Length) bytes, SHA-256 $sha256"
}
$checksums = [ordered]@{ packs = @($records) } | ConvertTo-Json -Depth 20 -Compress
Write-IfChanged (Join-Path $destination 'checksums.json') ($utf8.GetBytes($checksums + "`n"))
