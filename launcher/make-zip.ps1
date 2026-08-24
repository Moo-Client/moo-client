Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sourceDir = (Resolve-Path "$PSScriptRoot\dist-win\win-unpacked").Path
$zipPath = Join-Path "$PSScriptRoot\dist-win" "moo-client-launcher-win64.zip"

if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Write-Host "Tworzenie archiwum: $zipPath..."
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)

Get-ChildItem -Path $sourceDir -Recurse | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
    $relPath = $_.FullName.Substring($sourceDir.Length + 1)
    $entry = $zip.CreateEntry($relPath, [System.IO.Compression.CompressionLevel]::Optimal)
    $fs = [System.IO.File]::Open($_.FullName, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $entryStream = $entry.Open()
    $fs.CopyTo($entryStream)
    $entryStream.Close()
    $fs.Close()
}

$zip.Dispose()
$sizeMb = [Math]::Round(((Get-Item $zipPath).Length / 1MB), 2)
Write-Host "[SUKCES] Paczka utworzona: $zipPath ($sizeMb MB)"
