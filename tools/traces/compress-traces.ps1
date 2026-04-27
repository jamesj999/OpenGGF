[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Path = "tools/bizhawk/trace_output",

    [long]$ThresholdBytes = 1048576,

    [switch]$Recurse,

    [switch]$KeepOriginal
)

$ErrorActionPreference = "Stop"

function Get-StreamSha256AndLength([System.IO.Stream]$Stream) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $buffer = New-Object byte[] 1048576
        [long]$total = 0
        while (($read = $Stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $total += $read
            [void]$sha.TransformBlock($buffer, 0, $read, $null, 0)
        }
        [void]$sha.TransformFinalBlock([byte[]]::new(0), 0, 0)
        return [pscustomobject]@{
            Length = $total
            Hash = ([BitConverter]::ToString($sha.Hash) -replace "-", "").ToLowerInvariant()
        }
    } finally {
        $sha.Dispose()
    }
}

function Get-FileSha256AndLength([string]$FilePath) {
    $stream = [System.IO.File]::OpenRead($FilePath)
    try {
        return Get-StreamSha256AndLength $stream
    } finally {
        $stream.Dispose()
    }
}

function Get-GzipSha256AndLength([string]$FilePath) {
    $stream = [System.IO.File]::OpenRead($FilePath)
    try {
        $gzip = [System.IO.Compression.GZipStream]::new($stream, [System.IO.Compression.CompressionMode]::Decompress)
        try {
            return Get-StreamSha256AndLength $gzip
        } finally {
            $gzip.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Compress-TracePayloadFile([System.IO.FileInfo]$File) {
    if ($File.Length -lt $ThresholdBytes) {
        Write-Host ("skip {0} ({1} bytes below threshold {2})" -f $File.FullName, $File.Length, $ThresholdBytes)
        return
    }

    $destination = "$($File.FullName).gz"
    $tempDestination = "$destination.tmp"
    if (Test-Path -LiteralPath $tempDestination) {
        Remove-Item -LiteralPath $tempDestination -Force
    }

    $sourceStats = Get-FileSha256AndLength $File.FullName

    $inputStream = [System.IO.File]::OpenRead($File.FullName)
    try {
        $outputStream = [System.IO.File]::Create($tempDestination)
        try {
            $gzip = [System.IO.Compression.GZipStream]::new(
                $outputStream,
                [System.IO.Compression.CompressionLevel]::Optimal)
            try {
                $inputStream.CopyTo($gzip)
            } finally {
                $gzip.Dispose()
            }
        } finally {
            $outputStream.Dispose()
        }
    } finally {
        $inputStream.Dispose()
    }

    $gzipStats = Get-GzipSha256AndLength $tempDestination
    if ($sourceStats.Length -ne $gzipStats.Length -or $sourceStats.Hash -ne $gzipStats.Hash) {
        Remove-Item -LiteralPath $tempDestination -Force
        throw "gzip verification failed for $($File.FullName)"
    }

    Move-Item -LiteralPath $tempDestination -Destination $destination -Force
    $compressedLength = (Get-Item -LiteralPath $destination).Length

    if (-not $KeepOriginal) {
        Remove-Item -LiteralPath $File.FullName -Force
    }

    Write-Host ("compressed {0} -> {1} ({2} -> {3} bytes)" -f `
            $File.FullName, $destination, $File.Length, $compressedLength)
}

$resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
$item = Get-Item -LiteralPath $resolved

if ($item -is [System.IO.FileInfo]) {
    if ($item.Name -notlike "aux_state*.jsonl" -and $item.Name -notlike "physics*.csv") {
        throw "expected aux_state*.jsonl, physics*.csv, or directory, got $($item.FullName)"
    }
    Compress-TracePayloadFile $item
    exit 0
}

if ($Recurse) {
    $auxFiles = Get-ChildItem -LiteralPath $item.FullName -Filter "aux_state*.jsonl" -File -Recurse
    $physicsFiles = Get-ChildItem -LiteralPath $item.FullName -Filter "physics*.csv" -File -Recurse
} else {
    $auxFiles = Get-ChildItem -LiteralPath $item.FullName -Filter "aux_state*.jsonl" -File
    $physicsFiles = Get-ChildItem -LiteralPath $item.FullName -Filter "physics*.csv" -File
}

foreach ($file in @($auxFiles) + @($physicsFiles)) {
    Compress-TracePayloadFile $file
}
