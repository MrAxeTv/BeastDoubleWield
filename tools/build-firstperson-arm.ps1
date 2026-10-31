param(
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'target/firstperson-arm'),
    [string]$ClientResourcePackDirectory = 'C:/Users/krist/AppData/Roaming/.minecraft/resourcepacks',
    [switch]$SkipClientExport
)
# Owner-authorized ZIP export only; never edits client settings or starts/reloads a server.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'examples/firstperson-arm/26.3'))
$destination = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($destination) | Out-Null
$utf8 = New-Object Text.UTF8Encoding($false)

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

$contents = @{}
foreach ($asset in Get-ChildItem -LiteralPath $sourceRoot -Recurse) {
    if (($asset.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Pack sources cannot be symbolic links' }
    if ($asset.PSIsContainer) { continue }
    $name = $asset.FullName.Substring($sourceRoot.Length + 1).Replace('\', '/')
    if ($name -ne 'pack.mcmeta' -and (-not $name.StartsWith('assets/') -or @('.json','.vsh','.fsh','.glsl') -notcontains $asset.Extension)) { throw "Unexpected asset: $name" }
    $content = [IO.File]::ReadAllText($asset.FullName).Replace("`r`n", "`n").Replace("`r", "`n").TrimEnd() + "`n"
    if ($asset.Extension -eq '.json' -or $name -eq 'pack.mcmeta') { $null = $content | ConvertFrom-Json }
    $contents[$name] = $utf8.GetBytes($content)
}
[byte[]]$bytes = New-StoredZip $contents
$filename = 'BeastDoubleWield-firstperson-arm-26.3.zip'
$sha256 = Get-Digest $bytes 'SHA256'
$sha1 = Get-Digest $bytes 'SHA1'
Write-IfChanged (Join-Path $destination $filename) $bytes
Write-IfChanged (Join-Path $destination ($filename + '.sha256')) ($utf8.GetBytes($sha256 + "`n"))
Write-IfChanged (Join-Path $destination ($filename + '.sha1')) ($utf8.GetBytes($sha1 + "`n"))
$receipt = [ordered]@{profile='26.3';experimental=$true;file=$filename;bytes=$bytes.Length;sha256=$sha256;sha1=$sha1;entries=$contents.Count}
Write-IfChanged (Join-Path $destination 'checksums.json') ($utf8.GetBytes(($receipt | ConvertTo-Json -Compress) + "`n"))
if (-not $SkipClientExport) {
    if ([string]::IsNullOrWhiteSpace($ClientResourcePackDirectory)) { throw 'ClientResourcePackDirectory cannot be empty; use -SkipClientExport for an isolated build.' }
    $clientDirectory = [IO.Path]::GetFullPath($ClientResourcePackDirectory)
    if (-not [IO.Directory]::Exists($clientDirectory)) {
        throw "Client resource-pack folder does not exist: $clientDirectory. Choose -ClientResourcePackDirectory or use -SkipClientExport."
    }
    $clientPath = Join-Path $clientDirectory $filename
    Write-IfChanged $clientPath $bytes
    $clientHash = (Get-FileHash -LiteralPath $clientPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($clientHash -ne $sha256) { throw "Client pack export hash mismatch: $clientPath" }
    $receipt['clientExport'] = [ordered]@{path=$clientPath;sha256=$clientHash;verified=$true}
}
Write-Output ($receipt | ConvertTo-Json -Compress)
