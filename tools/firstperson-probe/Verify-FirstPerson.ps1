param(
    [string]$MinecraftRoot = (Join-Path $env:APPDATA '.minecraft'),
    [string]$PackRoot = (Join-Path $PSScriptRoot '../../examples/firstperson-arm/26.3'),
    [string]$ClientVersion = '26.3',
    [ValidateRange(320,7680)][int]$Width = 640,
    [ValidateRange(240,4320)][int]$Height = 480
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outputRoot = Join-Path $projectRoot 'target/firstperson-probe'
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$metadata = Get-Content -LiteralPath (Join-Path $MinecraftRoot "versions/$ClientVersion/$ClientVersion.json") -Raw | ConvertFrom-Json
$clientJar = Join-Path $MinecraftRoot "versions/$ClientVersion/$ClientVersion.jar"
$entries = [Collections.Generic.List[string]]::new()
$entries.Add($clientJar)
foreach ($library in $metadata.libraries) {
    if ($library.downloads.artifact.path) {
        $path = Join-Path $MinecraftRoot ('libraries/' + $library.downloads.artifact.path)
        if (Test-Path -LiteralPath $path -PathType Leaf) { $entries.Add($path) }
    }
}
$entries.Add($outputRoot)
# The 26.3 client uses SDL; this isolated probe uses matching GLFW. Missing
# GLFW artifacts are downloaded only into target, never into the client.
$lwjglVersion = ($metadata.libraries | Where-Object name -Match '^org\.lwjgl:lwjgl:[^:]+$' | Select-Object -First 1).name.Split(':')[2]
foreach ($suffix in @('', '-natives-windows')) {
    $glfw = Join-Path $MinecraftRoot "libraries/org/lwjgl/lwjgl-glfw/$lwjglVersion/lwjgl-glfw-$lwjglVersion$suffix.jar"
    if (-not (Test-Path -LiteralPath $glfw -PathType Leaf)) {
        $dependencyRoot = Join-Path $outputRoot 'dependencies'
        New-Item -ItemType Directory -Path $dependencyRoot -Force | Out-Null
        $glfw = Join-Path $dependencyRoot "lwjgl-glfw-$lwjglVersion$suffix.jar"
        if (-not (Test-Path -LiteralPath $glfw -PathType Leaf)) {
            Invoke-WebRequest -UseBasicParsing -Uri "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-glfw/$lwjglVersion/lwjgl-glfw-$lwjglVersion$suffix.jar" -OutFile $glfw
        }
    }
    $entries.Add($glfw)
}
function Quote-JavaArgument([string]$value) { '"' + $value.Replace('\', '/').Replace('"', '\"') + '"' }
$classpath = Quote-JavaArgument ($entries -join ';')
$compileArgs = @('-cp', $classpath, '-d', (Quote-JavaArgument $outputRoot), (Quote-JavaArgument (Join-Path $PSScriptRoot 'VerifyFirstPerson.java')))
$runArgs = @('--enable-native-access=ALL-UNNAMED', "-Dprobe.width=$Width", "-Dprobe.height=$Height", '-cp', $classpath, 'VerifyFirstPerson',
    (Quote-JavaArgument ([IO.Path]::GetFullPath($PackRoot))), (Quote-JavaArgument $clientJar), (Quote-JavaArgument $outputRoot))
$compileFile = Join-Path $outputRoot 'javac.args'
$runFile = Join-Path $outputRoot 'java.args'
[IO.File]::WriteAllLines($compileFile, $compileArgs)
[IO.File]::WriteAllLines($runFile, $runArgs)
Push-Location $outputRoot
try {
    & javac "@$compileFile"
    if ($LASTEXITCODE -ne 0) { throw "First-person probe compilation failed ($LASTEXITCODE)." }
    & java "@$runFile"
    if ($LASTEXITCODE -ne 0) { throw "First-person probe failed ($LASTEXITCODE)." }
} finally { Pop-Location }
